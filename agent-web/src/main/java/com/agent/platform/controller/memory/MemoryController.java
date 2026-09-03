package com.agent.platform.controller.memory;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.memory.MemItem;
import com.agent.platform.dao.entity.memory.MemStrategy;
import com.agent.platform.dao.entity.memory.MemVariable;
import com.agent.platform.service.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记忆管理接口：记忆策略 / 会话变量 / 长期记忆条目
 *
 * <p>URL：/api/memory/*</p>
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    // ---------- 记忆策略 ----------

    /** 获取应用记忆策略（不存在时返回默认策略） */
    @GetMapping("/strategy")
    public Result<MemStrategy> getStrategy(@RequestParam Long appId) {
        return Result.ok(memoryService.getStrategy(appId));
    }

    /** 保存应用记忆策略（按 appId upsert） */
    @PutMapping("/strategy")
    public Result<MemStrategy> saveStrategy(@RequestBody MemStrategy strategy) {
        return Result.ok(memoryService.saveStrategy(strategy));
    }

    // ---------- 会话变量 ----------

    @GetMapping("/apps/{appId}/variables")
    public Result<List<MemVariable>> variables(@PathVariable Long appId,
                                               @RequestParam(required = false) String scope,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(memoryService.listVariables(appId, scope, keyword));
    }

    @PostMapping("/apps/{appId}/variables")
    public Result<MemVariable> createVariable(@PathVariable Long appId,
                                              @RequestBody MemVariable variable) {
        return Result.ok(memoryService.createVariable(appId, variable));
    }

    @PutMapping("/variables/{id}")
    public Result<Void> updateVariable(@PathVariable Long id, @RequestBody MemVariable variable) {
        memoryService.updateVariable(id, variable);
        return Result.ok();
    }

    @DeleteMapping("/variables/{id}")
    public Result<Void> deleteVariable(@PathVariable Long id) {
        memoryService.deleteVariable(id);
        return Result.ok();
    }

    // ---------- 长期记忆条目 ----------

    @GetMapping("/apps/{appId}/items")
    public Result<List<MemItem>> items(@PathVariable Long appId,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam(required = false) String keyword) {
        return Result.ok(memoryService.listItems(appId, category, scope, keyword));
    }

    @PostMapping("/apps/{appId}/items")
    public Result<MemItem> createItem(@PathVariable Long appId, @RequestBody MemItem item) {
        return Result.ok(memoryService.createItem(appId, item));
    }

    @PutMapping("/items/{id}")
    public Result<Void> updateItem(@PathVariable Long id, @RequestBody MemItem item) {
        memoryService.updateItem(id, item);
        return Result.ok();
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        memoryService.deleteItem(id);
        return Result.ok();
    }
}
