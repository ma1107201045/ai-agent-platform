package com.agent.platform.dao.mapper.app;

import com.agent.platform.dao.entity.app.AppSchedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用定时任务 Mapper
 */
@Mapper
public interface AppScheduleMapper extends BaseMapper<AppSchedule> {
}
