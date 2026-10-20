package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
import com.agent.platform.common.security.RequireAdmin;
import com.agent.platform.service.sys.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 回收站接口
 *
 * <p>URL：/api/sys/trash（规范路径）；/api/system/trash 为兼容旧前端的别名，后续版本移除</p>
 */
@RestController
@RequestMapping({"/api/sys/trash", "/api/system/trash"})
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    /** 回收站列表（type 为空返回全部，支持 agent/dataset/template/announcement/team/tool/skill/prompt_template/conversation/data_table） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String type) {
        return Result.ok(trashService.list(type));
    }

    /** 彻底删除前的影响预览：返回将被级联删除的子数据计数（供确认框展示） */
    @GetMapping("/impact")
    public Result<Map<String, Long>> impact(@RequestParam String type, @RequestParam Long id) {
        return Result.ok(trashService.impact(type, id));
    }

    /** 恢复 {type,id} */
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody Map<String, Object> body) {
        trashService.restore(String.valueOf(body.get("type")), toId(body.get("id")));
        return Result.ok();
    }

    /** 彻底删除 {type,id}（不可恢复，仅管理员） */
    @PostMapping("/purge")
    @RequireAdmin
    public Result<Void> purge(@RequestBody Map<String, Object> body) {
        trashService.purge(String.valueOf(body.get("type")), toId(body.get("id")));
        return Result.ok();
    }

    /** 清理超过保留期(默认30天)的回收站数据 {days?}（仅管理员） */
    @PostMapping("/cleanup")
    @RequireAdmin
    public Result<Map<String, Object>> cleanup(@RequestBody(required = false) Map<String, Object> body) {
        int days = body == null || body.get("days") == null
                ? TrashService.DEFAULT_RETENTION_DAYS
                : ((Number) body.get("days")).intValue();
        int removed = trashService.cleanup(days);
        return Result.ok(Map.of("removed", removed, "days", days));
    }

    private Long toId(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        return ((Number) value).longValue();
    }
}
