package com.agent.platform.controller.chat;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.chat.ChatMessageFeedback;
import com.agent.platform.dao.vo.chat.ChatLabelMessageVO;
import com.agent.platform.service.chat.ChatFeedbackService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 对话标注接口
 *
 * <p>URL：/api/chat/feedbacks</p>
 */
@RestController
@RequestMapping("/api/chat/feedbacks")
@RequiredArgsConstructor
public class ChatFeedbackController {

    private final ChatFeedbackService feedbackService;

    /** 标注统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(feedbackService.stats());
    }

    /** 待标注/已标注助手消息分页（labeled 0未标注 1已标注；rating good/bad 精确过滤） */
    @GetMapping("/messages")
    public Result<Page<ChatLabelMessageVO>> messages(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long size,
                                                     @RequestParam(required = false) Long appId,
                                                     @RequestParam(required = false) Integer labeled,
                                                     @RequestParam(required = false) String rating,
                                                     @RequestParam(required = false) String keyword) {
        return Result.ok(feedbackService.pageMessages(page, size, appId, labeled, rating, keyword));
    }

    /** 保存 / 更新反馈：{messageId, rating, labelType?, correctedAnswer?, note?} */
    @PostMapping
    public Result<ChatMessageFeedback> save(@RequestBody Map<String, Object> body) {
        Long messageId = body.get("messageId") == null ? null : Long.valueOf(String.valueOf(body.get("messageId")));
        String rating = body.get("rating") == null ? null : String.valueOf(body.get("rating"));
        String labelType = body.get("labelType") == null ? null : String.valueOf(body.get("labelType"));
        String correctedAnswer = body.get("correctedAnswer") == null ? null : String.valueOf(body.get("correctedAnswer"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        return Result.ok(feedbackService.saveFeedback(messageId, rating, labelType, correctedAnswer, note));
    }

    /** 删除反馈（取消标注） */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        feedbackService.removeFeedback(id);
        return Result.ok();
    }
}
