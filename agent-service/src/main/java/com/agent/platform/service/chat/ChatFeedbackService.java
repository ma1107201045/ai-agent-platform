package com.agent.platform.service.chat;

import com.agent.platform.dao.entity.chat.ChatMessageFeedback;
import com.agent.platform.dao.vo.chat.ChatLabelMessageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 对话标注服务：查询可标注的助手消息、保存/更新/删除消息反馈、标注统计。
 */
public interface ChatFeedbackService {

    public Map<String, Object> stats();

    /**
     * @param labeled 0未标注 1已标注 空=全部
     * @param rating  好评good/差评bad（精确到评分）
     */
    public Page<ChatLabelMessageVO> pageMessages(long page, long size, Long appId, Integer labeled, String rating, String keyword);

    /** 按 messageId 保存反馈（存在则更新，否则新增） */
    public ChatMessageFeedback saveFeedback(Long messageId, String rating, String labelType, String correctedAnswer, String note);

    /** 删除反馈（取消标注） */
    public void removeFeedback(Long feedbackId);
}
