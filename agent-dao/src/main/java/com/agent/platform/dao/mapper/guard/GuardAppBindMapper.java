package com.agent.platform.dao.mapper.guard;

import com.agent.platform.dao.entity.guard.GuardAppBind;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容安全-应用绑定 Mapper
 */
@Mapper
public interface GuardAppBindMapper extends BaseMapper<GuardAppBind> {
}
