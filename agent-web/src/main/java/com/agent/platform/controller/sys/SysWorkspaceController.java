package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.entity.sys.SysTenant;
import com.agent.platform.dao.vo.sys.SysWorkspaceVO;
import com.agent.platform.service.sys.SysWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作空间接口
 *
 * <p>URL：/api/sys/workspace</p>
 */
@RestController
@RequestMapping("/api/sys/workspace")
@RequiredArgsConstructor
public class SysWorkspaceController {

    private final SysWorkspaceService workspaceService;

    @GetMapping("/current")
    public Result<SysWorkspaceVO> current() {
        return Result.ok(workspaceService.current());
    }

    /** 更新空间基础信息（名称 / 套餐），body 传 {name, plan} */
    @PutMapping("/current")
    public Result<SysWorkspaceVO> update(@RequestBody SysTenant tenant) {
        return Result.ok(workspaceService.update(tenant));
    }
}
