package com.agent.platform.dao.mapper.sys;

import com.agent.platform.dao.entity.sys.SysAnnouncement;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 平台公告 Mapper
 */
@Mapper
public interface SysAnnouncementMapper extends BaseMapper<SysAnnouncement> {

    /** 软删除：移入回收站并记录删除时间 */
    @Update("UPDATE sys_announcement SET deleted = 1, deleted_time = NOW() WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id);
}
