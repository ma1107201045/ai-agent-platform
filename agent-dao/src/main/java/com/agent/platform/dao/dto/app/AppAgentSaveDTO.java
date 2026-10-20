package com.agent.platform.dao.dto.app;

import com.agent.platform.dao.entity.app.AppAgent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 应用创建/编辑请求 DTO。
 * <p>
 * 仅暴露用户可编辑的业务字段；tenantId、status、publishedVersionId、
 * deleted 等内部字段由服务端管理，不接受客户端传入（防越权/状态篡改）。
 */
@Data
public class AppAgentSaveDTO {

    @NotBlank(message = "应用名称不能为空")
    @Size(max = 100, message = "应用名称不能超过 100 字")
    private String name;

    @Size(max = 500, message = "应用描述不能超过 500 字")
    private String description;

    /** 应用类型：chatflow(对话流) / workflow(工作流) / agent(自主智能体) */
    private String type;

    private String icon;

    /** 开场白 */
    private String welcomeMessage;

    /** 推荐问题（JSON 数组字符串） */
    private String openingQuestions;

    /** 编排草稿 DSL（JSON） */
    private String workflowJson;

    /** 关联工具 ID 列表（JSON 数组字符串） */
    private String toolIds;

    /** 关联数据集 ID 列表（JSON 数组字符串） */
    private String datasetIds;

    /** 绑定技能 ID 列表（JSON 数组字符串） */
    private String skillIds;

    /** DTO → 实体（仅拷贝可编辑字段，内部字段保持 null 由服务端处理） */
    public AppAgent toEntity() {
        AppAgent app = new AppAgent();
        app.setName(name);
        app.setDescription(description);
        app.setType(type);
        app.setIcon(icon);
        app.setWelcomeMessage(welcomeMessage);
        app.setOpeningQuestions(openingQuestions);
        app.setWorkflowJson(workflowJson);
        app.setToolIds(toolIds);
        app.setDatasetIds(datasetIds);
        app.setSkillIds(skillIds);
        return app;
    }
}
