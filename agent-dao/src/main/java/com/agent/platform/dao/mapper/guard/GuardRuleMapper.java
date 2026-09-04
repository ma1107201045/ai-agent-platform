package com.agent.platform.dao.mapper.guard;

import com.agent.platform.dao.entity.guard.GuardRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 内容安全-规则库 Mapper
 */
@Mapper
public interface GuardRuleMapper extends BaseMapper<GuardRule> {
}
