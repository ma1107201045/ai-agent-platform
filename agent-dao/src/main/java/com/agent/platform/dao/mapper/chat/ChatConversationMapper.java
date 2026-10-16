package com.agent.platform.dao.mapper.chat;

import com.agent.platform.dao.entity.chat.ChatConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

    /** 软删除：移入回收站并记录删除时间 */
    @Update("UPDATE chat_conversation SET deleted = 1, deleted_time = NOW() WHERE id = #{id} AND deleted = 0")
    int markDeleted(@Param("id") Long id);
}
