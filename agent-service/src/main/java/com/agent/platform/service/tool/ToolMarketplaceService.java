package com.agent.platform.service.tool;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.app.AppAgentTool;
import com.agent.platform.dao.mapper.app.AppAgentToolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件市场服务：内置工具模板目录 + 一键安装到当前工作空间。
 *
 * <p>模板定义由服务端内置维护（随版本更新补充），安装即创建一条真实的
 * {@link AppAgentTool} 记录，复用既有 HTTP / 代码工具执行能力，模型可立即调用。
 *
 * <p>约定：Template.name 即安装后的工具名（英文标识符）；目录中与现有工具同名的模板视为「已安装」。
 */
@Service
@RequiredArgsConstructor
public class ToolMarketplaceService {

    private static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    /** 内置模板目录（category：basic 通用 / text 文本处理 / web 网络数据） */
    private static final List<Template> TEMPLATES = buildTemplates();

    private final AppAgentToolMapper toolMapper;

    // ---------- 模板定义 ----------

    @Data
    public static class Template {
        /** 模板唯一标识（安装接口使用） */
        private String key;
        /** 工具名称（英文标识符，安装后即 app_agent_tool.name） */
        private String name;
        private String description;
        private String category;
        /** http / code */
        private String type;
        private String method;
        private String url;
        private String code;
        /** 参数 JSON Schema */
        private String parameters;
    }

    /** 市场卡片视图：模板字段 + 是否已安装 */
    @Data
    public static class TemplateVO {
        private String key;
        private String name;
        private String description;
        private String category;
        private String type;
        private String method;
        private String url;
        private String code;
        private String parameters;
        private boolean installed;
    }

    private static Template tpl(String key, String name, String description, String category,
                                String type, String method, String url, String code, String parameters) {
        Template t = new Template();
        t.setKey(key);
        t.setName(name);
        t.setDescription(description);
        t.setCategory(category);
        t.setType(type);
        t.setMethod(method);
        t.setUrl(url);
        t.setCode(code);
        t.setParameters(parameters);
        return t;
    }

    private static List<Template> buildTemplates() {
        List<Template> list = new ArrayList<>();
        // ---- basic 通用 ----
        list.add(tpl("random_uuid", "random_uuid",
                "生成一个随机的 UUID v4 字符串，可用于消息、任务、订单等标识生成",
                "basic", "code", "GET", null,
                """
                return java.util.UUID.randomUUID().toString();
                """,
                EMPTY_SCHEMA));
        // ---- text 文本处理 ----
        list.add(tpl("text_convert", "text_convert",
                "文本格式转换：按 mode 参数对文本执行转大写 / 转小写 / 反转 / 字数统计",
                "text", "code", "GET", null,
                """
                var s = text != null ? String.valueOf(text) : "";
                var m = mode != null ? String.valueOf(mode) : "uppercase";
                if (m.equals("lowercase")) return s.toLowerCase();
                if (m.equals("reverse")) return new StringBuilder(s).reverse().toString();
                if (m.equals("count")) {
                    var t = s.trim();
                    var words = t.isEmpty() ? 0 : t.split(" +").length;
                    return "字符数=" + s.length() + ", 单词数=" + words;
                }
                return s.toUpperCase();
                """,
                """
                {
                  "type": "object",
                  "properties": {
                    "text": { "type": "string", "description": "待处理的文本内容" },
                    "mode": { "type": "string", "enum": ["uppercase", "lowercase", "reverse", "count"],
                              "description": "处理方式：uppercase 转大写 / lowercase 转小写 / reverse 反转 / count 统计字符与单词数" }
                  },
                  "required": ["text"]
                }
                """));
        // ---- web 网络数据 ----
        list.add(tpl("ip_location", "ip_location",
                "查询当前出口公网 IP 的地理位置（国家/城市/经纬度），基于 ipapi.co 免费接口，无需密钥",
                "web", "http", "GET", "https://ipapi.co/json/", null, EMPTY_SCHEMA));
        list.add(tpl("httpbin_echo", "httpbin_echo",
                "向 httpbin.org 发送回显请求并返回你传入的参数，用于验证 HTTP 工具参数拼装与连通性",
                "web", "http", "GET", "https://httpbin.org/anything", null,
                """
                {
                  "type": "object",
                  "properties": {
                    "value": { "type": "string", "description": "任意测试值，将以 query 参数发送到 httpbin" }
                  },
                  "required": ["value"]
                }
                """));
        return List.copyOf(list);
    }

    // ---------- 市场查询与安装 ----------

    /**
     * 模板目录（含已安装标记）。可按分类 / 关键字过滤。
     */
    public List<TemplateVO> templates(String category, String keyword) {
        Set<String> names = toolMapper.selectList(new LambdaQueryWrapper<AppAgentTool>()
                        .select(AppAgentTool::getName))
                .stream().map(AppAgentTool::getName).collect(Collectors.toSet());
        return TEMPLATES.stream()
                .filter(t -> category == null || category.isBlank() || category.equals(t.getCategory()))
                .filter(t -> keyword == null || keyword.isBlank()
                        || t.getName().toLowerCase().contains(keyword.toLowerCase())
                        || t.getDescription().toLowerCase().contains(keyword.toLowerCase()))
                .map(t -> toVO(t, names.contains(t.getName())))
                .collect(Collectors.toList());
    }

    private TemplateVO toVO(Template t, boolean installed) {
        TemplateVO vo = new TemplateVO();
        vo.setKey(t.getKey());
        vo.setName(t.getName());
        vo.setDescription(t.getDescription());
        vo.setCategory(t.getCategory());
        vo.setType(t.getType());
        vo.setMethod(t.getMethod());
        vo.setUrl(t.getUrl());
        vo.setCode(t.getCode());
        vo.setParameters(t.getParameters());
        vo.setInstalled(installed);
        return vo;
    }

    /**
     * 按模板一键安装：以模板内容创建一条真实工具记录。
     */
    public AppAgentTool install(String key) {
        Template t = TEMPLATES.stream()
                .filter(x -> x.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new BizException("模板不存在: " + key));
        Long exists = toolMapper.selectCount(new LambdaQueryWrapper<AppAgentTool>()
                .eq(AppAgentTool::getName, t.getName()));
        if (exists != null && exists > 0) {
            throw new BizException("「" + t.getName() + "」已存在于工具管理中，无需重复安装，可直接前往工具管理编辑使用");
        }
        AppAgentTool tool = new AppAgentTool();
        tool.setTenantId(1L);
        tool.setName(t.getName());
        tool.setDescription(t.getDescription());
        tool.setType(t.getType());
        tool.setUrl(t.getUrl());
        tool.setMethod(t.getMethod() == null ? "GET" : t.getMethod());
        tool.setAuthType("none");
        tool.setParameters(t.getParameters());
        tool.setCode(t.getCode());
        tool.setStatus(1);
        toolMapper.insert(tool);
        return tool;
    }
}
