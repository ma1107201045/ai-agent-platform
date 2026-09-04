package com.agent.platform.controller.model;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.model.ModelGatewayRoute;
import com.agent.platform.service.model.ModelGatewayService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 模型网关路由接口
 *
 * <p>URL：/api/model/gateway</p>
 */
@RestController
@RequestMapping("/api/model/gateway")
@RequiredArgsConstructor
public class ModelGatewayController {

    private final ModelGatewayService gatewayService;

    @GetMapping
    public Result<Page<ModelGatewayRoute>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer enabled) {
        return Result.ok(gatewayService.page(page, size, keyword, enabled));
    }

    @GetMapping("/{id}")
    public Result<ModelGatewayRoute> get(@PathVariable Long id) {
        return Result.ok(gatewayService.get(id));
    }

    @PostMapping
    public Result<ModelGatewayRoute> create(@RequestBody Body body) {
        return Result.ok(gatewayService.create(body.route, body.targets));
    }

    @PutMapping("/{id}")
    public Result<ModelGatewayRoute> update(@PathVariable Long id, @RequestBody Body body) {
        return Result.ok(gatewayService.update(id, body.route, body.targets));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        gatewayService.delete(id);
        return Result.ok();
    }

    /** 路由决策模拟 */
    @PostMapping("/{id}/simulate")
    public Result<Map<String, Object>> simulate(@PathVariable Long id) {
        return Result.ok(gatewayService.simulate(id));
    }

    /** 接收 body：{route: {...}, targets: [{modelId,weight,priority}]} */
    public static class Body {
        public ModelGatewayRoute route;
        public List<Map<String, Object>> targets;
    }
}
