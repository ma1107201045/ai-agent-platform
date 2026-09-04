package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.sys.SysAnnouncement;
import com.agent.platform.service.sys.SysAnnouncementService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 平台公告接口
 *
 * <p>URL：/api/sys/announcements</p>
 */
@RestController
@RequestMapping("/api/sys/announcements")
@RequiredArgsConstructor
public class SysAnnouncementController {

    private final SysAnnouncementService announcementService;

    @GetMapping
    public Result<Page<SysAnnouncement>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status) {
        return Result.ok(announcementService.page(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<SysAnnouncement> getById(@PathVariable Long id) {
        return Result.ok(announcementService.getById(id));
    }

    @PostMapping
    public Result<SysAnnouncement> create(@RequestBody SysAnnouncement announcement) {
        return Result.ok(announcementService.create(announcement));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysAnnouncement announcement) {
        announcementService.update(id, announcement);
        return Result.ok();
    }

    /** 发布（自动向全体用户广播站内通知） */
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        announcementService.publish(id);
        return Result.ok();
    }

    /** 下线 */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        announcementService.offline(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.ok();
    }
}
