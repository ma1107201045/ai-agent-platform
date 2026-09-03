package com.agent.platform.dao.mapper.memory;

import com.agent.platform.dao.entity.memory.MemVariable;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话变量 Mapper
 */
@Mapper
public interface MemVariableMapper extends BaseMapper<MemVariable> {
}
