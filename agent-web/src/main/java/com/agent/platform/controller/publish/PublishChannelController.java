package com.agent.platform.controller.publish;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.publish.PublishChannel;
import com.agent.platform.dao.entity.publish.PublishChannelMsg;
import com.agent.platform.service.publish.PublishChannelService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布-渠道管理接口
 *
 * <p>URL：/api/publish/channels</p>
 * <p>注意：{@code POST /{id}/callback} 为三方平台回调入口，已在 WebConfig 放行（无需登录）。</p>
 */
@RestController
@RequestMapping("/api/publish/channels")
@RequiredArgsConstructor
public class PublishChannelController {

    private final PublishChannelService channelService;

    @GetMapping
    public Result<Page<PublishChannel>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) Long appId,
                                             @RequestParam(required = false) String channelType,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(channelService.page(page, size, appId, channelType, keyword));
    }

    @GetMapping("/{id}")
    public Result<PublishChannel> get(@PathVariable Long id) {
        return Result.ok(channelService.getById(id));
    }

    @PostMapping
    public Result<PublishChannel> create(@RequestBody PublishChannel channel) {
        return Result.ok(channelService.create(channel));
    }

    @PutMapping("/{id}")
    public Result<PublishChannel> update(@PathVariable Long id, @RequestBody PublishChannel req) {
        return Result.ok(channelService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        channelService.delete(id);
        return Result.ok();
    }

    /** 启停开关 */
    @PutMapping("/{id}/enabled")
    public Result<PublishChannel> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        return Result.ok(channelService.toggle(id, enabled));
    }

    /** 配置校验（不改库，仅返回缺失的必填配置键） */
    @PostMapping("/config/validate")
    public Result<Map<String, Object>> validate(@RequestBody Map<String, String> req) {
        List<String> missing = channelService.validateConfig(req.get("channelType"), req.get("configJson"));
        Map<String, Object> result = new HashMap<>();
        result.put("valid", missing.isEmpty());
        result.put("missing", missing);
        return Result.ok(result);
    }

    /** 三方渠道回调（WebConfig 放行）。body: {content?, fromUser?} */
    @PostMapping("/{id}/callback")
    public Result<PublishChannelMsg> callback(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, String> body) {
        if (body == null) {
            body = new HashMap<>();
        }
        return Result.ok(channelService.receive(id, body.get("content"), body.get("fromUser")));
    }

    /** 渠道消息分页 */
    @GetMapping("/{id}/messages")
    public Result<Page<PublishChannelMsg>> messages(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "20") long size,
                                                    @RequestParam(required = false) String direction,
                                                    @RequestParam(required = false) String keyword) {
        return Result.ok(channelService.messages(page, size, id, direction, keyword));
    }

    /** 渠道统计 */
    @GetMapping("/{id}/stats")
    public Result<Map<String, Object>> stats(@PathVariable Long id) {
        return Result.ok(channelService.stats(id));
    }
}
