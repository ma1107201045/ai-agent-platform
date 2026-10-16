package com.agent.platform.service.publish;

import com.agent.platform.dao.entity.publish.PublishChannel;
import com.agent.platform.dao.entity.publish.PublishChannelMsg;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Map;

/**
 * 发布渠道服务：渠道 CRUD + 配置校验 + 回调消息处理与统计
 */
public interface PublishChannelService {

    public Page<PublishChannel> page(long page, long size, Long appId, String channelType, String keyword);

    public PublishChannel getById(Long id);

    public PublishChannel create(PublishChannel channel);

    public PublishChannel update(Long id, PublishChannel req);

    public void delete(Long id);

    /** 启停开关 */
    public PublishChannel toggle(Long id, Integer enabled);

    /** 校验必填配置，返回缺失键列表；无缺失返回空 */
    public List<String> validateConfig(String channelType, String configJson);

    /**
     * 渠道回调：收到终端入站消息后调用绑定应用并记录消息。
     * 仅 enabled 且应用已发布时执行回复；否则记录 skipped。
     */
    public PublishChannelMsg receive(Long channelId, String content, String fromUser);

    public Page<PublishChannelMsg> messages(long page, long size, Long channelId, String direction, String keyword);

    /** 渠道消息统计：总数/今日/失败/近7日趋势 */
    public Map<String, Object> stats(Long channelId);
}
