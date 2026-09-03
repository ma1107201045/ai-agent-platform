package com.agent.platform.controller.orchestrator;

import com.agent.platform.common.result.Result;
import com.agent.platform.orchestrator.node.NodeHandlerRegistry;
import com.agent.platform.orchestrator.node.NodeTypeSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 编排器元数据接口（节点 Schema 单源化）
 * <p>
 * 前端节点面板 / 配置表单以此为准渲染，不再手写维护节点字段与默认值。
 */
@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class NodeSchemaController {

    private final NodeHandlerRegistry nodeHandlerRegistry;

    /** 全部节点类型 Schema（含配置字段 / 默认值 / 分支与连线约束） */
    @GetMapping("/node-types")
    public Result<List<NodeTypeSchema>> nodeTypes() {
        return Result.ok(nodeHandlerRegistry.schemas());
    }
}
