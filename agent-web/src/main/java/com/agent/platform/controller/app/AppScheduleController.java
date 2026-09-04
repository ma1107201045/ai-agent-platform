package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.app.AppSchedule;
import com.agent.platform.dao.entity.app.AppScheduleLog;
import com.agent.platform.service.app.AppScheduleService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 应用定时任务接口
 *
 * <p>URL：/api/app/schedules</p>
 */
@RestController
@RequestMapping("/api/app/schedules")
@RequiredArgsConstructor
public class AppScheduleController {

    private final AppScheduleService scheduleService;

    @GetMapping
    public Result<Page<AppSchedule>> page(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer enabled) {
        return Result.ok(scheduleService.page(page, size, keyword, enabled));
    }

    @GetMapping("/{id}")
    public Result<AppSchedule> getById(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }

    @PostMapping
    public Result<AppSchedule> create(@RequestBody AppSchedule schedule) {
        return Result.ok(scheduleService.create(schedule));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AppSchedule schedule) {
        scheduleService.update(id, schedule);
        return Result.ok();
    }

    /** 启用 / 停用 */
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        scheduleService.setEnabled(id, enabled);
        return Result.ok();
    }

    /** 立即执行一次，返回 {status, message, costMs} */
    @PostMapping("/{id}/run")
    public Result<Map<String, String>> runNow(@PathVariable Long id) {
        return Result.ok(scheduleService.runNow(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/logs")
    public Result<Page<AppScheduleLog>> logs(@PathVariable Long id,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(scheduleService.logPage(id, page, size));
    }
}
