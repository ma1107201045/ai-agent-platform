package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysAnnouncement;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 平台公告服务：公告的创建、发布/下线与删除。
 *
 * <p>发布公告时会自动向当前租户全部启用用户广播一条站内通知。</p>
 */
public interface SysAnnouncementService {

    public static final int STATUS_DRAFT = 0;

    public static final int STATUS_PUBLISHED = 1;

    public static final int STATUS_OFFLINE = 2;

    public Page<SysAnnouncement> page(long page, long size, String keyword, Integer status);

    public SysAnnouncement getById(Long id);

    /** 新建（草稿） */
    public SysAnnouncement create(SysAnnouncement announcement);

    public void update(Long id, SysAnnouncement announcement);

    /** 发布：状态置为发布中，并向全体用户广播站内通知 */
    public void publish(Long id);

    /** 下线：状态置为已下线 */
    public void offline(Long id);

    public void delete(Long id);
}
