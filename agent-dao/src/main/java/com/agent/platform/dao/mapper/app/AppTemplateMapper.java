package com.agent.platform.dao.mapper.app;

import com.agent.platform.dao.entity.app.AppTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 应用模板 Mapper
 */
@Mapper
public interface AppTemplateMapper extends BaseMapper<AppTemplate> {

    /** 软删除：移入回收站并记录删除时间 */
    @Update("UPDATE app_template SET deleted = 1, deleted_time = NOW() WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id);
}
