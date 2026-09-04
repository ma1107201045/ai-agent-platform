package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.sys.SysNotification;
import com.agent.platform.service.sys.SysNotificationService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知接口（当前登录用户的消息收件箱）
 *
 * <p>URL：/api/sys/notifications</p>
 */
@RestController
@RequestMapping("/api/sys/notifications")
@RequiredArgsConstructor
public class SysNotificationController {

    private final SysNotificationService notificationService;

    @GetMapping
    public Result<Page<SysNotification>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) Integer read) {
        return Result.ok(notificationService.page(page, size, type, read));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.unreadCount());
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok();
    }

    /** 全部标为已读 */
    @PutMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.markReadAll();
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        notificationService.remove(id);
        return Result.ok();
    }
}
