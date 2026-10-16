package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysNotification;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 站内通知服务：面向当前登录用户的消息收件箱
 *
 * <p>通知来源包括：公告发布广播、定时任务结果、系统告警等。</p>
 */
public interface SysNotificationService {

    public static final String TYPE_SYSTEM = "system";

    public static final String TYPE_ANNOUNCEMENT = "announcement";

    public static final String TYPE_RUN = "run";

    public static final String TYPE_ALERT = "alert";

    public Page<SysNotification> page(long page, long size, String type, Integer read);

    /** 未读数量 */
    public long unreadCount();

    /** 标记单条为已读（仅限自己的消息） */
    public void markRead(Long id);

    /** 全部标为已读 */
    public long markReadAll();

    /** 删除单条（仅限自己的消息） */
    public void remove(Long id);

    /** 给单个用户发送通知 */
    public void send(Long userId, String type, String title, String content,
                     String bizType, Long bizId);

    /**
     * 向当前租户全部启用的用户广播通知（如公告发布、系统升级提示）。
     *
     * @return 实际投递条数
     */
    public long broadcast(String type, String title, String content,
                          String bizType, Long bizId);
}
