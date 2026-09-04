package com.agent.platform.service.chat;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.chat.ChatConversation;
import com.agent.platform.dao.entity.chat.ChatMessage;
import com.agent.platform.dao.entity.chat.ChatMessageFeedback;
import com.agent.platform.dao.entity.sys.SysUser;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.chat.ChatConversationMapper;
import com.agent.platform.dao.mapper.chat.ChatMessageFeedbackMapper;
import com.agent.platform.dao.mapper.chat.ChatMessageMapper;
import com.agent.platform.dao.mapper.sys.SysUserMapper;
import com.agent.platform.dao.vo.chat.ChatLabelMessageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 对话标注服务：查询可标注的助手消息、保存/更新/删除消息反馈、标注统计。
 */
@Service
@RequiredArgsConstructor
public class ChatFeedbackService {

    private final ChatMessageMapper messageMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageFeedbackMapper feedbackMapper;
    private final AppAgentMapper appAgentMapper;
    private final SysUserMapper userMapper;

    // ---------- 统计 ----------

    public Map<String, Object> stats() {
        Long total = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "assistant")
                .apply("conversation_id IN (SELECT id FROM chat_conversation WHERE tenant_id = {0})", tenant()));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalMessages", total == null ? 0 : total);
        map.put("labeledCount", feedbackCount(null));
        map.put("goodCount", feedbackCount("good"));
        map.put("badCount", feedbackCount("bad"));
        // 标注覆盖率
        long labeled = (Long) map.get("labeledCount");
        long totalMsg = (Long) map.get("totalMessages");
        map.put("coverage", totalMsg == 0 ? 0 : Math.round(labeled * 1000.0 / totalMsg) / 10.0);
        return map;
    }

    private long feedbackCount(String rating) {
        LambdaQueryWrapper<ChatMessageFeedback> qw = new LambdaQueryWrapper<ChatMessageFeedback>()
                .eq(ChatMessageFeedback::getTenantId, tenant());
        if (rating != null) {
            qw.eq(ChatMessageFeedback::getRating, rating);
        }
        Long c = feedbackMapper.selectCount(qw);
        return c == null ? 0 : c;
    }

    // ---------- 待标注消息分页 ----------

    /**
     * @param labeled 0未标注 1已标注 空=全部
     * @param rating  好评good/差评bad（精确到评分）
     */
    public Page<ChatLabelMessageVO> pageMessages(long page, long size, Long appId, Integer labeled, String rating, String keyword) {
        LambdaQueryWrapper<ChatMessage> qw = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getRole, "assistant")
                .apply("conversation_id IN (SELECT id FROM chat_conversation WHERE tenant_id = {0})", tenant());
        if (appId != null && appId > 0) {
            qw.apply("conversation_id IN (SELECT id FROM chat_conversation WHERE tenant_id = {0} AND app_id = {1})", tenant(), appId);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(ChatMessage::getContent, keyword.trim()));
        }
        if (rating != null) {
            qw.and(w -> w.exists("SELECT 1 FROM chat_message_feedback f WHERE f.message_id = chat_message.id AND f.rating = {0}", rating));
        } else if (labeled != null) {
            if (labeled == 1) {
                qw.and(w -> w.exists("SELECT 1 FROM chat_message_feedback f WHERE f.message_id = chat_message.id"));
            } else {
                qw.and(w -> w.notExists("SELECT 1 FROM chat_message_feedback f WHERE f.message_id = chat_message.id"));
            }
        }
        qw.orderByDesc(ChatMessage::getId);
        Page<ChatMessage> p = messageMapper.selectPage(new Page<>(page, size), qw);
        return toVos(p);
    }

    private Page<ChatLabelMessageVO> toVos(Page<ChatMessage> p) {
        List<ChatMessage> records = p.getRecords();
        if (records.isEmpty()) {
            return new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        }
        Set<Long> convIds = records.stream().map(ChatMessage::getConversationId).collect(Collectors.toSet());
        Set<Long> msgIds = records.stream().map(ChatMessage::getId).collect(Collectors.toSet());
        // 会话
        Map<Long, ChatConversation> convMap = convIds.isEmpty() ? Collections.emptyMap()
                : conversationMapper.selectBatchIds(convIds).stream().collect(Collectors.toMap(ChatConversation::getId, c -> c));
        // 应用名
        Set<Long> appIds = convMap.values().stream()
                .map(ChatConversation::getAppId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, String> appNameMap = appIds.isEmpty() ? Collections.emptyMap()
                : appAgentMapper.selectBatchIds(appIds).stream().collect(Collectors.toMap(AppAgent::getId, AppAgent::getName));
        // 反馈
        Map<Long, ChatMessageFeedback> fbMap = msgIds.isEmpty() ? Collections.emptyMap()
                : feedbackMapper.selectList(new LambdaQueryWrapper<ChatMessageFeedback>()
                        .in(ChatMessageFeedback::getMessageId, msgIds)
                        .orderByDesc(ChatMessageFeedback::getId)).stream()
                        .collect(Collectors.toMap(ChatMessageFeedback::getMessageId, f -> f, (a, b) -> a));
        // 标注人昵称
        Set<Long> userIds = fbMap.values().stream()
                .map(ChatMessageFeedback::getCreatedBy)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> userNameMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream().collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));

        List<ChatLabelMessageVO> vos = new ArrayList<>();
        for (ChatMessage m : records) {
            ChatLabelMessageVO vo = new ChatLabelMessageVO();
            vo.setMessageId(m.getId());
            vo.setConversationId(m.getConversationId());
            ChatConversation conv = convMap.get(m.getConversationId());
            vo.setConversationTitle(conv == null ? "未命名会话" : StringUtils.hasText(conv.getTitle()) ? conv.getTitle() : "未命名会话");
            Long appId = conv == null ? null : conv.getAppId();
            vo.setAppId(appId == null || appId <= 0 ? 0L : appId);
            vo.setAppName(appId == null || appId <= 0 ? "直连模型" : appNameMap.getOrDefault(appId, "应用 #" + appId));
            vo.setContent(m.getContent());
            vo.setAssistant(Boolean.TRUE);
            vo.setCreateTime(m.getCreateTime());
            ChatMessageFeedback fb = fbMap.get(m.getId());
            vo.setLabeled(fb != null);
            if (fb != null) {
                vo.setFeedbackId(fb.getId());
                vo.setRating(fb.getRating());
                vo.setLabelType(fb.getLabelType());
                vo.setCorrectedAnswer(fb.getCorrectedAnswer());
                vo.setNote(fb.getNote());
                vo.setCreatedByName(fb.getCreatedBy() == null ? null : userNameMap.getOrDefault(fb.getCreatedBy(), "用户 #" + fb.getCreatedBy()));
                vo.setFeedbackTime(fb.getCreateTime());
            }
            vos.add(vo);
        }
        Page<ChatLabelMessageVO> out = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(vos);
        return out;
    }

    // ---------- 反馈 CRUD ----------

    /** 按 messageId 保存反馈（存在则更新，否则新增） */
    public ChatMessageFeedback saveFeedback(Long messageId, String rating, String labelType, String correctedAnswer, String note) {
        if (messageId == null) {
            throw new BizException("消息ID不能为空");
        }
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException("消息不存在: " + messageId);
        }
        if (!"good".equals(rating) && !"bad".equals(rating)) {
            throw new BizException("评分必须为 good(好) 或 bad(差)");
        }
        ChatConversation conv = message.getConversationId() == null ? null
                : conversationMapper.selectById(message.getConversationId());
        // 会话租户校验
        if (conv != null && conv.getTenantId() != null && !conv.getTenantId().equals(tenant())) {
            throw new BizException("无权标注该消息");
        }
        Long appId = conv == null || conv.getAppId() == null || conv.getAppId() <= 0 ? 0L : conv.getAppId();
        Long userId = conv == null ? null : conv.getUserId();

        ChatMessageFeedback fb = feedbackMapper.selectList(new LambdaQueryWrapper<ChatMessageFeedback>()
                .eq(ChatMessageFeedback::getMessageId, messageId)
                .orderByDesc(ChatMessageFeedback::getId)
                .last("limit 1")).stream().findFirst().orElse(null);
        if (fb != null) {
            fb.setRating(rating);
            fb.setLabelType(StringUtils.hasText(labelType) ? labelType : null);
            fb.setCorrectedAnswer(StringUtils.hasText(correctedAnswer) ? correctedAnswer : null);
            fb.setNote(StringUtils.hasText(note) ? note : null);
            feedbackMapper.updateById(fb);
            return fb;
        }
        fb = new ChatMessageFeedback();
        fb.setTenantId(tenant());
        fb.setMessageId(messageId);
        fb.setConversationId(message.getConversationId());
        fb.setAppId(appId);
        fb.setUserId(userId);
        fb.setRating(rating);
        fb.setLabelType(StringUtils.hasText(labelType) ? labelType : null);
        fb.setCorrectedAnswer(StringUtils.hasText(correctedAnswer) ? correctedAnswer : null);
        fb.setNote(StringUtils.hasText(note) ? note : null);
        fb.setCreatedBy(UserContext.getUserId());
        fb.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(fb);
        return fb;
    }

    /** 删除反馈（取消标注） */
    public void removeFeedback(Long feedbackId) {
        if (feedbackId == null) {
            return;
        }
        ChatMessageFeedback fb = feedbackMapper.selectById(feedbackId);
        if (fb == null) {
            return;
        }
        if (!fb.getTenantId().equals(tenant())) {
            throw new BizException("无权操作该反馈");
        }
        feedbackMapper.deleteById(feedbackId);
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
