package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppPromptTemplate;
import com.agent.platform.dao.entity.app.AppPromptVersion;
import com.agent.platform.service.app.AppPromptTemplateService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板（AppPromptTemplate）管理接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_prompt_template → 实体 AppPromptTemplate → Mapper AppPromptTemplateMapper
 *                         → Service AppPromptTemplateService → 本类 AppPromptTemplateController
 *                         → URL /api/app/prompts（kebab-case 复数）
 * </pre>
 * <p>同时提供版本快照（versions / rollback）与试跑渲染（render）能力。
 */
@RestController
@RequestMapping("/api/app/prompts")
@RequiredArgsConstructor
public class AppPromptTemplateController {

    private final AppPromptTemplateService promptService;

    // ---------- 模板管理 ----------

    @GetMapping
    public Result<Page<AppPromptTemplate>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category) {
        return Result.ok(promptService.page(page, size, keyword, category));
    }

    @GetMapping("/enabled")
    public Result<List<AppPromptTemplate>> enabled(@RequestParam(required = false) String category) {
        return Result.ok(promptService.listEnabled(category));
    }

    @GetMapping("/{id}")
    public Result<AppPromptTemplate> getById(@PathVariable Long id) {
        return Result.ok(promptService.getById(id));
    }

    @PostMapping
    public Result<AppPromptTemplate> create(@RequestBody AppPromptTemplate template) {
        return Result.ok(promptService.create(template));
    }

    @PutMapping("/{id}")
    public Result<AppPromptTemplate> update(@PathVariable Long id, @RequestBody AppPromptTemplate template) {
        template.setId(id);
        return Result.ok(promptService.update(template));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.delete(id);
        return Result.ok();
    }

    // ---------- 版本管理 ----------

    @GetMapping("/{id}/versions")
    public Result<List<AppPromptVersion>> versions(@PathVariable Long id) {
        return Result.ok(promptService.versions(id));
    }

    /** 回退到指定历史版本（生成新版本留痕） */
    @PostMapping("/{id}/rollback")
    public Result<AppPromptTemplate> rollback(@PathVariable Long id, @RequestBody RollbackReq req) {
        return Result.ok(promptService.rollback(id, req.getVersion()));
    }

    // ---------- 试跑渲染 ----------

    /** 渲染模板正文（{{var}} 占位替换），用于在线调试 */
    @PostMapping("/render")
    public Result<String> render(@RequestBody RenderReq req) {
        return Result.ok(promptService.render(req.getContent(), req.getVariables()));
    }

    /** 提取正文中的变量占位名 */
    @PostMapping("/extract-variables")
    public Result<List<String>> extractVariables(@RequestBody ExtractReq req) {
        return Result.ok(promptService.extractVariables(req.getContent()));
    }

    /** 解析变量定义 JSON 为 name→desc 映射（供试跑表单使用） */
    @PostMapping("/parse-variables")
    public Result<Map<String, String>> parseVariables(@RequestBody ParseReq req) {
        return Result.ok(promptService.parseVariableDefs(req.getVariables()));
    }

    @Data
    public static class RollbackReq {
        private Integer version;
    }

    @Data
    public static class RenderReq {
        private String content;
        private Map<String, Object> variables;
    }

    @Data
    public static class ExtractReq {
        private String content;
    }

    @Data
    public static class ParseReq {
        private String variables;
    }
}
