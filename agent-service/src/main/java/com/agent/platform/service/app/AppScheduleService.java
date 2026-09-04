package com.agent.platform.service.app;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.common.security.UserContext;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppSchedule;
import com.agent.platform.dao.entity.app.AppScheduleLog;
import com.agent.platform.dao.mapper.app.AppAgentMapper;
import com.agent.platform.dao.mapper.app.AppScheduleLogMapper;
import com.agent.platform.dao.mapper.app.AppScheduleMapper;
import com.agent.platform.service.common.AppExecuteService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应用定时任务服务：任务配置、手动立即执行与后台周期调度。
 *
 * <p>调度说明：平台以进程内轮询调度器实现（30s 粒度），执行时真实调用
 * {@link AppExecuteService#runApp} 运行关联应用（应用须处于已发布状态）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppScheduleService {

    public static final String TRIGGER_INTERVAL = "interval";
    public static final String TRIGGER_DAILY = "daily";
    public static final String TRIGGER_WEEKLY = "weekly";

    /** 手动触发时的默认输入 */
    private static final String DEFAULT_INPUT = "定时任务触发，请开始执行。";
    private static final int SCAN_BATCH = 50;
    private static final int MSG_MAX = 500;

    private final AppScheduleMapper scheduleMapper;
    private final AppScheduleLogMapper logMapper;
    private final AppAgentMapper appAgentMapper;
    private final AppExecuteService appExecuteService;

    /* ==================== 查询 ==================== */

    public Page<AppSchedule> page(long page, long size, String keyword, Integer enabled) {
        LambdaQueryWrapper<AppSchedule> wrapper = new LambdaQueryWrapper<AppSchedule>()
                .eq(AppSchedule::getTenantId, tenant())
                .orderByDesc(AppSchedule::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AppSchedule::getName, keyword)
                    .or().like(AppSchedule::getAppName, keyword)
                    .or().like(AppSchedule::getRemark, keyword));
        }
        if (enabled != null) {
            wrapper.eq(AppSchedule::getEnabled, enabled);
        }
        Page<AppSchedule> result = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        fillAppName(result.getRecords());
        return result;
    }

    public AppSchedule getById(Long id) {
        AppSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || !schedule.getTenantId().equals(tenant())) {
            throw new BizException("定时任务不存在: " + id);
        }
        return schedule;
    }

    public Page<AppScheduleLog> logPage(Long scheduleId, long page, long size) {
        AppSchedule schedule = getById(scheduleId);
        return logMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AppScheduleLog>()
                        .eq(AppScheduleLog::getTenantId, tenant())
                        .eq(AppScheduleLog::getScheduleId, schedule.getId())
                        .orderByDesc(AppScheduleLog::getId));
    }

    /* ==================== 增删改 ==================== */

    public AppSchedule create(AppSchedule schedule) {
        validate(schedule);
        LocalDateTime now = LocalDateTime.now();
        schedule.setId(null);
        schedule.setTenantId(tenant());
        schedule.setAppName(resolveAppName(schedule.getAppId()));
        schedule.setEnabled(schedule.getEnabled() == null ? 1 : schedule.getEnabled());
        schedule.setCreateBy(UserContext.getUserId());
        schedule.setCreateTime(now);
        schedule.setUpdateTime(now);
        schedule.setLastRunTime(null);
        schedule.setNextRunTime(schedule.getEnabled() == 1 ? nextRunTime(schedule, now) : null);
        scheduleMapper.insert(schedule);
        return schedule;
    }

    public void update(Long id, AppSchedule schedule) {
        AppSchedule exist = getById(id);
        validate(schedule);
        schedule.setId(id);
        schedule.setTenantId(exist.getTenantId());
        schedule.setAppName(resolveAppName(schedule.getAppId()));
        schedule.setCreateBy(exist.getCreateBy());
        schedule.setCreateTime(exist.getCreateTime());
        if (schedule.getEnabled() == null) {
            schedule.setEnabled(exist.getEnabled());
        }
        schedule.setLastRunTime(exist.getLastRunTime());
        LocalDateTime now = LocalDateTime.now();
        schedule.setUpdateTime(now);
        // 重新计算下一次执行时间
        schedule.setNextRunTime(schedule.getEnabled() == 1 ? nextRunTime(schedule, now) : null);
        scheduleMapper.updateById(schedule);
    }

    public void setEnabled(Long id, boolean enabled) {
        AppSchedule exist = getById(id);
        LocalDateTime now = LocalDateTime.now();
        exist.setEnabled(enabled ? 1 : 0);
        exist.setUpdateTime(now);
        exist.setNextRunTime(enabled ? nextRunTime(exist, now) : null);
        scheduleMapper.updateById(exist);
    }

    public void delete(Long id) {
        AppSchedule schedule = getById(id);
        scheduleMapper.deleteById(schedule.getId());
        logMapper.delete(new LambdaQueryWrapper<AppScheduleLog>()
                .eq(AppScheduleLog::getScheduleId, schedule.getId()));
    }

    /** 立即执行一次（手动） */
    public Map<String, String> runNow(Long id) {
        AppSchedule schedule = getById(id);
        return execute(schedule, "manual");
    }

    /* ==================== 后台调度 ==================== */

    /** 每 30 秒扫描一次到期任务 */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void scanDue() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<AppSchedule> due = scheduleMapper.selectList(new LambdaQueryWrapper<AppSchedule>()
                    .eq(AppSchedule::getEnabled, 1)
                    .isNotNull(AppSchedule::getNextRunTime)
                    .le(AppSchedule::getNextRunTime, now)
                    .orderByAsc(AppSchedule::getNextRunTime)
                    .last("limit " + SCAN_BATCH));
            for (AppSchedule schedule : due) {
                try {
                    execute(schedule, "scheduled");
                } catch (Exception e) {
                    log.warn("定时任务自动执行失败 scheduleId={}", schedule.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("定时任务扫描器异常", e);
        }
    }

    /** 执行一次任务并写入执行记录（自动推进下次执行时间） */
    private Map<String, String> execute(AppSchedule schedule, String triggerBy) {
        LocalDateTime now = LocalDateTime.now();
        long start = System.currentTimeMillis();
        String status;
        String message;
        try {
            String input = StringUtils.hasText(schedule.getInputMessage())
                    ? schedule.getInputMessage() : DEFAULT_INPUT;
            AppExecuteService.Reply reply = appExecuteService.runApp(schedule.getAppId(), input);
            status = "success";
            message = summarize(reply == null || reply.answer() == null ? "执行完成" : reply.answer());
        } catch (Exception e) {
            status = "failed";
            message = summarize(extractMessage(e));
        }
        long cost = System.currentTimeMillis() - start;

        AppScheduleLog runLog = new AppScheduleLog();
        runLog.setTenantId(schedule.getTenantId());
        runLog.setScheduleId(schedule.getId());
        runLog.setScheduleName(schedule.getName());
        runLog.setAppId(schedule.getAppId());
        runLog.setAppName(schedule.getAppName());
        runLog.setTriggerBy(triggerBy);
        runLog.setStatus(status);
        runLog.setMessage(message);
        runLog.setCostMs((int) cost);
        runLog.setCreateTime(now);
        logMapper.insert(runLog);

        schedule.setLastRunTime(now);
        schedule.setUpdateTime(now);
        schedule.setNextRunTime(schedule.getEnabled() != null && schedule.getEnabled() == 1
                ? nextRunTime(schedule, now) : null);
        scheduleMapper.updateById(schedule);
        return Map.of("status", status, "message", message, "costMs", String.valueOf(cost));
    }

    /* ==================== 私有工具 ==================== */

    private void validate(AppSchedule schedule) {
        if (schedule == null || !StringUtils.hasText(schedule.getName())) {
            throw new BizException("任务名称不能为空");
        }
        if (schedule.getAppId() == null) {
            throw new BizException("请选择要执行的应用");
        }
        String type = schedule.getTriggerType() == null ? TRIGGER_INTERVAL : schedule.getTriggerType();
        if (TRIGGER_INTERVAL.equals(type)) {
            if (schedule.getIntervalMinutes() == null || schedule.getIntervalMinutes() < 1) {
                throw new BizException("触发间隔必须大于 0 分钟");
            }
        } else if (TRIGGER_DAILY.equals(type)) {
            if (!validRunTime(schedule.getRunTime())) {
                throw new BizException("请选择每天的执行时刻");
            }
        } else if (TRIGGER_WEEKLY.equals(type)) {
            if (schedule.getRunWeekday() == null || schedule.getRunWeekday() < 1
                    || schedule.getRunWeekday() > 7 || !validRunTime(schedule.getRunTime())) {
                throw new BizException("请选择执行星期与时刻");
            }
        } else {
            throw new BizException("不支持的触发类型: " + type);
        }
    }

    private String resolveAppName(Long appId) {
        AppAgent app = appAgentMapper.selectById(appId);
        if (app == null) {
            throw new BizException("关联应用不存在: " + appId);
        }
        return app.getName();
    }

    private void fillAppName(List<AppSchedule> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(AppSchedule::getAppId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = appAgentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AppAgent::getId, AppAgent::getName, (a, b) -> a));
        for (AppSchedule record : records) {
            String name = names.get(record.getAppId());
            if (name != null) {
                record.setAppName(name);
            }
        }
    }

    /** 计算 from 之后的下一次执行时间 */
    private LocalDateTime nextRunTime(AppSchedule schedule, LocalDateTime from) {
        String type = schedule.getTriggerType() == null ? TRIGGER_INTERVAL : schedule.getTriggerType();
        LocalDateTime base = from == null ? LocalDateTime.now() : from;
        if (TRIGGER_INTERVAL.equals(type)) {
            int minutes = schedule.getIntervalMinutes() == null ? 5 : schedule.getIntervalMinutes();
            return base.plusMinutes(Math.max(1, minutes));
        }
        LocalTime time;
        try {
            time = LocalTime.parse(schedule.getRunTime());
        } catch (DateTimeParseException | NullPointerException e) {
            time = LocalTime.of(9, 0);
        }
        if (TRIGGER_DAILY.equals(type)) {
            LocalDateTime candidate = LocalDateTime.of(base.toLocalDate(), time);
            return candidate.isAfter(base) ? candidate : candidate.plusDays(1);
        }
        int weekday = schedule.getRunWeekday() == null ? 1 : schedule.getRunWeekday();
        DayOfWeek target = DayOfWeek.of(Math.max(1, Math.min(7, weekday)));
        int diff = (target.getValue() - base.getDayOfWeek().getValue() + 7) % 7;
        if (diff == 0) {
            diff = 7;
        }
        LocalDate date = base.toLocalDate().plusDays(diff);
        LocalDateTime candidate = LocalDateTime.of(date, time);
        return candidate.isAfter(base) ? candidate : candidate.plusDays(7);
    }

    private boolean validRunTime(String runTime) {
        if (!StringUtils.hasText(runTime)) {
            return false;
        }
        try {
            LocalTime.parse(runTime.trim());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private String summarize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= MSG_MAX ? flat : flat.substring(0, MSG_MAX) + "…";
    }

    private String extractMessage(Exception e) {
        String msg = e.getMessage();
        if (StringUtils.hasText(msg)) {
            return msg;
        }
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
            if (StringUtils.hasText(cause.getMessage())) {
                return cause.getMessage();
            }
        }
        return e.getClass().getSimpleName();
    }

    private Long tenant() {
        Long tenantId = UserContext.getTenantId();
        return tenantId == null ? 1L : tenantId;
    }
}
