package com.agent.platform.service.sys;

import com.agent.platform.dao.entity.sys.SysAnnouncement;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 平台公告服务：公告的创建、发布/下线与删除。
 *
 * <p>发布公告时会自动向当前租户全部启用用户广播一条站内通知。</p>
 */
public interface SysAnnouncementService {

    static final int STATUS_DRAFT = 0;

    static final int STATUS_PUBLISHED = 1;

    static final int STATUS_OFFLINE = 2;

    Page<SysAnnouncement> page(long page, long size, String keyword, Integer status);

    SysAnnouncement getById(Long id);

    /** 新建（草稿） */
    SysAnnouncement create(SysAnnouncement announcement);

    void update(Long id, SysAnnouncement announcement);

    /** 发布：状态置为发布中，并向全体用户广播站内通知 */
    void publish(Long id);

    /** 下线：状态置为已下线 */
    void offline(Long id);

    void delete(Long id);
}
