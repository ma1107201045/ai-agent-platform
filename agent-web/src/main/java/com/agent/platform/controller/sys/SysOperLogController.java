package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.sys.SysOperLog;
import com.agent.platform.service.sys.SysOperLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询接口
 *
 * <p>URL：/api/sys/oper-logs（写入由 OperationLogAspect 自动完成）</p>
 */
@RestController
@RequestMapping("/api/sys/oper-logs")
@RequiredArgsConstructor
public class SysOperLogController {

    private final SysOperLogService operLogService;

    @GetMapping
    public Result<Page<SysOperLog>> page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String module,
                                         @RequestParam(required = false) Integer success,
                                         @RequestParam(required = false) String startTime,
                                         @RequestParam(required = false) String endTime) {
        return Result.ok(operLogService.page(page, size, keyword, module, success, startTime, endTime));
    }
}
