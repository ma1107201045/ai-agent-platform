package com.agent.platform.controller.sys;

import com.agent.platform.common.result.Result;
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
 * <p>URL：/api/system/trash</p>
 */
@RestController
@RequestMapping("/api/system/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    /** 回收站列表（type 为空返回全部，支持 agent/dataset/template/announcement） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String type) {
        return Result.ok(trashService.list(type));
    }

    /** 恢复 {type,id} */
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody Map<String, Object> body) {
        trashService.restore(String.valueOf(body.get("type")), toId(body.get("id")));
        return Result.ok();
    }

    /** 彻底删除 {type,id}（不可恢复） */
    @PostMapping("/purge")
    public Result<Void> purge(@RequestBody Map<String, Object> body) {
        trashService.purge(String.valueOf(body.get("type")), toId(body.get("id")));
        return Result.ok();
    }

    /** 清理超过保留期(默认30天)的回收站数据 {days?} */
    @PostMapping("/cleanup")
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
