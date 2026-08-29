package com.agent.platform.dao.mapper.chat;

import com.agent.platform.dao.entity.chat.ChatConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
