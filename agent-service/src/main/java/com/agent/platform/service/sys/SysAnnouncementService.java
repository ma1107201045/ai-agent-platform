package com.agent.platform.service.sys;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.sys.SysAnnouncement;
import com.agent.platform.dao.mapper.sys.SysAnnouncementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 平台公告服务：公告的创建、发布/下线与删除。
 *
 * <p>发布公告时会自动向当前租户全部启用用户广播一条站内通知。</p>
 */
@Service
@RequiredArgsConstructor
public class SysAnnouncementService {

    /** 公告内容摘要的最大长度（通知内容） */
    private static final int SUMMARY_LENGTH = 300;

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_OFFLINE = 2;

    private final SysAnnouncementMapper announcementMapper;
    private final SysNotificationService notificationService;

    public Page<SysAnnouncement> page(long page, long size, String keyword, Integer status) {
        LambdaQueryWrapper<SysAnnouncement> wrapper = new LambdaQueryWrapper<SysAnnouncement>()
                .eq(SysAnnouncement::getTenantId, tenant())
                .orderByDesc(SysAnnouncement::getPinned)
                .orderByDesc(SysAnnouncement::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysAnnouncement::getTitle, keyword)
                    .or().like(SysAnnouncement::getContent, keyword));
        }
        if (status != null) {
            wrapper.eq(SysAnnouncement::getStatus, status);
        }
        return announcementMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public SysAnnouncement getById(Long id) {
        SysAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException("公告不存在: " + id);
        }
        return announcement;
    }

    /** 新建（草稿） */
    public SysAnnouncement create(SysAnnouncement announcement) {
        validate(announcement);
        LocalDateTime now = LocalDateTime.now();
        announcement.setId(null);
        announcement.setTenantId(tenant());
        announcement.setScope(StringUtils.hasText(announcement.getScope()) ? announcement.getScope() : "all");
        if (announcement.getStatus() == null) {
            announcement.setStatus(STATUS_DRAFT);
        }
        if (announcement.getPinned() == null) {
            announcement.setPinned(0);
        }
        announcement.setPublisher(UserContext.getUserId());
        announcement.setCreateTime(now);
        announcement.setUpdateTime(now);
        announcementMapper.insert(announcement);
        return announcement;
    }

    public void update(Long id, SysAnnouncement announcement) {
        SysAnnouncement exist = getById(id);
        validate(announcement);
        announcement.setId(id);
        announcement.setTenantId(exist.getTenantId());
        announcement.setScope(StringUtils.hasText(announcement.getScope()) ? announcement.getScope() : "all");
        if (announcement.getPinned() == null) {
            announcement.setPinned(exist.getPinned());
        }
        if (announcement.getStatus() == null) {
            announcement.setStatus(exist.getStatus());
        }
        announcement.setPublisher(exist.getPublisher());
        announcement.setPublishTime(exist.getPublishTime());
        announcement.setOfflineTime(exist.getOfflineTime());
        announcement.setCreateTime(exist.getCreateTime());
        announcement.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(announcement);
    }

    /** 发布：状态置为发布中，并向全体用户广播站内通知 */
    public void publish(Long id) {
        SysAnnouncement announcement = getById(id);
        if (announcement.getStatus() != null && announcement.getStatus() == STATUS_PUBLISHED) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        announcement.setStatus(STATUS_PUBLISHED);
        announcement.setPublishTime(now);
        announcement.setOfflineTime(null);
        announcement.setUpdateTime(now);
        announcementMapper.updateById(announcement);

        notificationService.broadcast(
                SysNotificationService.TYPE_ANNOUNCEMENT,
                announcement.getTitle(),
                summarize(announcement.getContent()),
                "announcement",
                announcement.getId());
    }

    /** 下线：状态置为已下线 */
    public void offline(Long id) {
        SysAnnouncement announcement = getById(id);
        if (announcement.getStatus() != null && announcement.getStatus() == STATUS_OFFLINE) {
            return;
        }
        announcement.setStatus(STATUS_OFFLINE);
        announcement.setOfflineTime(LocalDateTime.now());
        announcement.setUpdateTime(LocalDateTime.now());
        announcementMapper.updateById(announcement);
    }

    public void delete(Long id) {
        getById(id);
        announcementMapper.markDeleted(id);
    }

    private void validate(SysAnnouncement announcement) {
        if (announcement == null || !StringUtils.hasText(announcement.getTitle())) {
            throw new BizException("公告标题不能为空");
        }
        if (announcement.getTitle().length() > 128) {
            throw new BizException("公告标题不能超过 128 字");
        }
        if (!StringUtils.hasText(announcement.getContent())) {
            throw new BizException("公告内容不能为空");
        }
    }

    private String summarize(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String flat = content.replaceAll("\\s+", " ");
        return flat.length() <= SUMMARY_LENGTH ? flat : flat.substring(0, SUMMARY_LENGTH) + "…";
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
