package com.agent.platform.dao.mapper.chat;

import com.agent.platform.dao.entity.chat.ChatMessageFeedback;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话标注-消息反馈 Mapper
 */
@Mapper
public interface ChatMessageFeedbackMapper extends BaseMapper<ChatMessageFeedback> {
}
