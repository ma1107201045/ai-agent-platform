package com.agent.platform.dao.mapper.memory;

import com.agent.platform.dao.entity.memory.MemItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 长期记忆条目 Mapper
 */
@Mapper
public interface MemItemMapper extends BaseMapper<MemItem> {
}
