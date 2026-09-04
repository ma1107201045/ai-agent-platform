package com.agent.platform.service.sys;

import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.sys.SysNotification;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.mapper.sys.SysNotificationMapper;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知服务：面向当前登录用户的消息收件箱
 *
 * <p>通知来源包括：公告发布广播、定时任务结果、系统告警等。</p>
 */
@Service
@RequiredArgsConstructor
public class SysNotificationService {

    private final SysNotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;

    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_ANNOUNCEMENT = "announcement";
    public static final String TYPE_RUN = "run";
    public static final String TYPE_ALERT = "alert";

    public Page<SysNotification> page(long page, long size, String type, Integer read) {
        LambdaQueryWrapper<SysNotification> wrapper = new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, tenant())
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .orderByDesc(SysNotification::getId);
        if (StringUtils.hasText(type)) {
            wrapper.eq(SysNotification::getType, type);
        }
        if (read != null) {
            wrapper.eq(SysNotification::getRead, read);
        }
        return notificationMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 未读数量 */
    public long unreadCount() {
        return notificationMapper.selectCount(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, tenant())
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .eq(SysNotification::getRead, 0));
    }

    /** 标记单条为已读（仅限自己的消息） */
    public void markRead(Long id) {
        notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getTenantId, tenant())
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .eq(SysNotification::getRead, 0)
                .set(SysNotification::getRead, 1)
                .set(SysNotification::getReadTime, LocalDateTime.now()));
    }

    /** 全部标为已读 */
    public long markReadAll() {
        return notificationMapper.update(null, new LambdaUpdateWrapper<SysNotification>()
                .eq(SysNotification::getTenantId, tenant())
                .eq(SysNotification::getUserId, UserContext.getUserId())
                .eq(SysNotification::getRead, 0)
                .set(SysNotification::getRead, 1)
                .set(SysNotification::getReadTime, LocalDateTime.now()));
    }

    /** 删除单条（仅限自己的消息） */
    public void remove(Long id) {
        notificationMapper.delete(new LambdaQueryWrapper<SysNotification>()
                .eq(SysNotification::getId, id)
                .eq(SysNotification::getTenantId, tenant())
                .eq(SysNotification::getUserId, UserContext.getUserId()));
    }

    /** 给单个用户发送通知 */
    public void send(Long userId, String type, String title, String content,
                     String bizType, Long bizId) {
        if (userId == null) {
            return;
        }
        insert(tenant(), userId, type, title, content, bizType, bizId);
    }

    /**
     * 向当前租户全部启用的用户广播通知（如公告发布、系统升级提示）。
     *
     * @return 实际投递条数
     */
    public long broadcast(String type, String title, String content,
                          String bizType, Long bizId) {
        Long tenantId = tenant();
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getStatus, 1)
                .select(SysUser::getId));
        if (CollectionUtils.isEmpty(users)) {
            return 0;
        }
        long count = 0;
        for (SysUser user : users) {
            insert(tenantId, user.getId(), type, title, content, bizType, bizId);
            count++;
        }
        return count;
    }

    private void insert(Long tenantId, Long userId, String type, String title, String content,
                        String bizType, Long bizId) {
        SysNotification notification = new SysNotification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setRead(0);
        LocalDateTime now = LocalDateTime.now();
        notification.setCreateTime(now);
        notification.setUpdateTime(now);
        notificationMapper.insert(notification);
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
