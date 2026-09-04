package com.agent.platform.service.publish;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.publish.PublishChannel;
import com.agent.platform.dao.entity.publish.PublishChannelMsg;
import com.agent.platform.dao.mapper.publish.PublishChannelMapper;
import com.agent.platform.dao.mapper.publish.PublishChannelMsgMapper;
import com.agent.platform.service.common.AppExecuteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 发布渠道服务：渠道 CRUD + 配置校验 + 回调消息处理与统计
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishChannelService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 不同类型渠道在 config_json 中的必填配置键 */
    private static final Map<String, Set<String>> REQUIRED_KEYS = Map.of(
            "wechat_mp", Set.of("token", "encodingAesKey"),
            "feishu", Set.of("appId", "appSecret"),
            "dingtalk", Set.of("appKey", "appSecret"),
            "web", Set.of("token"),
            "webhook", Set.of()
    );

    private final PublishChannelMapper channelMapper;
    private final PublishChannelMsgMapper channelMsgMapper;
    private final AppExecuteService appExecuteService;
    private final ObjectMapper objectMapper;

    // ---------- 渠道 CRUD ----------

    public Page<PublishChannel> page(long page, long size, Long appId, String channelType, String keyword) {
        Long tenantId = UserContext.getTenantId();
        LambdaQueryWrapper<PublishChannel> wrapper = new LambdaQueryWrapper<PublishChannel>()
                .eq(PublishChannel::getTenantId, tenantId)
                .eq(appId != null, PublishChannel::getAppId, appId)
                .eq(StringUtils.hasText(channelType), PublishChannel::getChannelType, channelType)
                .orderByDesc(PublishChannel::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(PublishChannel::getName, kw).or().like(PublishChannel::getDescription, kw));
        }
        return channelMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public PublishChannel getById(Long id) {
        PublishChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new BizException("渠道不存在: " + id);
        }
        return channel;
    }

    public PublishChannel create(PublishChannel channel) {
        validate(channel);
        LocalDateTime now = LocalDateTime.now();
        channel.setId(null);
        channel.setTenantId(UserContext.getTenantId());
        channel.setEnabled(channel.getEnabled() == null ? 1 : channel.getEnabled());
        channel.setMsgCount(0L);
        channel.setCreateTime(now);
        channel.setUpdateTime(now);
        channelMapper.insert(channel);
        return channel;
    }

    public PublishChannel update(Long id, PublishChannel req) {
        PublishChannel channel = getById(id);
        if (req.getAppId() != null) channel.setAppId(req.getAppId());
        if (StringUtils.hasText(req.getName())) channel.setName(req.getName().trim());
        if (StringUtils.hasText(req.getChannelType())) channel.setChannelType(req.getChannelType());
        if (req.getConfigJson() != null) channel.setConfigJson(req.getConfigJson());
        if (req.getDescription() != null) channel.setDescription(req.getDescription());
        channel.setUpdateTime(LocalDateTime.now());
        validate(channel);
        channelMapper.updateById(channel);
        return channel;
    }

    public void delete(Long id) {
        getById(id);
        channelMsgMapper.delete(new LambdaQueryWrapper<PublishChannelMsg>()
                .eq(PublishChannelMsg::getChannelId, id));
        channelMapper.deleteById(id);
    }

    /** 启停开关 */
    public PublishChannel toggle(Long id, Integer enabled) {
        PublishChannel channel = getById(id);
        channel.setEnabled(enabled != null && enabled == 1 ? 1 : 0);
        channel.setUpdateTime(LocalDateTime.now());
        channelMapper.updateById(channel);
        return channel;
    }

    // ---------- 配置校验 ----------

    /** 校验必填配置，返回缺失键列表；无缺失返回空 */
    public List<String> validateConfig(String channelType, String configJson) {
        List<String> missing = new ArrayList<>();
        Set<String> keys = REQUIRED_KEYS.getOrDefault(channelType == null ? "" : channelType, Set.of());
        Map<String, Object> config = parseConfig(configJson);
        for (String key : keys) {
            Object value = config.get(key);
            if (value == null || String.valueOf(value).isBlank()) {
                missing.add(key);
            }
        }
        return missing;
    }

    private void validate(PublishChannel channel) {
        if (channel.getAppId() == null) {
            throw new BizException("请选择绑定的应用");
        }
        if (!StringUtils.hasText(channel.getName())) {
            throw new BizException("请填写渠道名称");
        }
        if (!REQUIRED_KEYS.containsKey(channel.getChannelType())) {
            throw new BizException("不支持的渠道类型: " + channel.getChannelType());
        }
        List<String> missing = validateConfig(channel.getChannelType(), channel.getConfigJson());
        if (!missing.isEmpty()) {
            throw new BizException("渠道配置缺少必填项: " + String.join(", ", missing));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Object value = objectMapper.readValue(configJson, Object.class);
            return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    // ---------- 回调处理 ----------

    /**
     * 渠道回调：收到终端入站消息后调用绑定应用并记录消息。
     * 仅 enabled 且应用已发布时执行回复；否则记录 skipped。
     */
    public PublishChannelMsg receive(Long channelId, String content, String fromUser) {
        PublishChannel channel = getById(channelId);
        LocalDateTime now = LocalDateTime.now();

        PublishChannelMsg msg = new PublishChannelMsg();
        msg.setTenantId(channel.getTenantId());
        msg.setChannelId(channelId);
        msg.setAppId(channel.getAppId());
        msg.setDirection("inbound");
        msg.setEventType("message");
        msg.setFromUser(StringUtils.hasText(fromUser) ? fromUser : "anonymous");
        msg.setContent(content);
        msg.setCreateTime(now);

        if (channel.getEnabled() == null || channel.getEnabled() != 1) {
            msg.setStatus("skipped");
            msg.setErrorMsg("渠道已停用，消息未处理");
            channelMsgMapper.insert(msg);
            return msg;
        }
        try {
            AppExecuteService.Reply reply = appExecuteService.runApp(channel.getAppId(), content);
            msg.setReply(reply.answer());
            msg.setStatus("success");
        } catch (BizException e) {
            msg.setStatus("failed");
            msg.setErrorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("渠道回调执行失败 channelId={}", channelId, e);
            msg.setStatus("failed");
            msg.setErrorMsg("执行异常: " + e.getMessage());
        }
        channelMsgMapper.insert(msg);

        channel.setMsgCount((channel.getMsgCount() == null ? 0 : channel.getMsgCount()) + 1);
        channel.setLastMsgAt(now);
        channelMapper.updateById(channel);
        return msg;
    }

    // ---------- 消息与统计 ----------

    public Page<PublishChannelMsg> messages(long page, long size, Long channelId, String direction, String keyword) {
        LambdaQueryWrapper<PublishChannelMsg> wrapper = new LambdaQueryWrapper<PublishChannelMsg>()
                .eq(PublishChannelMsg::getChannelId, channelId)
                .eq(StringUtils.hasText(direction), PublishChannelMsg::getDirection, direction)
                .orderByDesc(PublishChannelMsg::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(PublishChannelMsg::getContent, kw)
                    .or().like(PublishChannelMsg::getReply, kw)
                    .or().like(PublishChannelMsg::getFromUser, kw));
        }
        return channelMsgMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 渠道消息统计：总数/今日/失败/近7日趋势 */
    public Map<String, Object> stats(Long channelId) {
        getById(channelId);
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<PublishChannelMsg> base = new LambdaQueryWrapper<PublishChannelMsg>()
                .eq(PublishChannelMsg::getChannelId, channelId);
        long total = channelMsgMapper.selectCount(base);
        long todayCount = channelMsgMapper.selectCount(base.clone()
                .ge(PublishChannelMsg::getCreateTime, today.atStartOfDay()));
        long failed = channelMsgMapper.selectCount(base.clone()
                .eq(PublishChannelMsg::getStatus, "failed"));

        List<Map<String, Object>> trend = new ArrayList<>();
        List<PublishChannelMsg> list = channelMsgMapper.selectList(base.clone()
                .ge(PublishChannelMsg::getCreateTime, today.minusDays(6).atStartOfDay())
                .select(PublishChannelMsg::getCreateTime));
        Map<String, Integer> countByDay = new LinkedHashMap<>();
        for (PublishChannelMsg m : list) {
            String day = m.getCreateTime() == null ? today.toString() : m.getCreateTime().toLocalDate().format(DAY);
            countByDay.merge(day, 1, Integer::sum);
        }
        for (int i = 6; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day);
            point.put("count", countByDay.getOrDefault(day, 0));
            trend.add(point);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("today", todayCount);
        result.put("failed", failed);
        result.put("trend", trend);
        return result;
    }
}
