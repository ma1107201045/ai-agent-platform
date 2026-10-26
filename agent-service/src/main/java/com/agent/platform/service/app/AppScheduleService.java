package com.agent.platform.service.app;

import com.agent.platform.dao.entity.app.AppSchedule;
import com.agent.platform.dao.entity.app.AppScheduleLog;
import com.agent.platform.service.common.AppExecuteService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Map;

/**
 * 应用定时任务服务：任务配置、手动立即执行与后台周期调度。
 *
 * <p>调度说明：平台以进程内轮询调度器实现（30s 粒度），执行时真实调用
 * {@link AppExecuteService#runApp} 运行关联应用（应用须处于已发布状态）。</p>
 */
public interface AppScheduleService {

    static final String TRIGGER_INTERVAL = "interval";

    static final String TRIGGER_DAILY = "daily";

    static final String TRIGGER_WEEKLY = "weekly";

    Page<AppSchedule> page(long page, long size, String keyword, Integer enabled);

    AppSchedule getById(Long id);

    Page<AppScheduleLog> logPage(Long scheduleId, long page, long size);

    AppSchedule create(AppSchedule schedule);

    void update(Long id, AppSchedule schedule);

    void setEnabled(Long id, boolean enabled);

    void delete(Long id);

    /** 立即执行一次（手动） */
    Map<String, String> runNow(Long id);
}
