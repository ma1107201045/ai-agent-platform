package com.agent.platform.dao.mapper.app;

import com.agent.platform.dao.entity.app.AppScheduleLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行记录 Mapper
 */
@Mapper
public interface AppScheduleLogMapper extends BaseMapper<AppScheduleLog> {
}
