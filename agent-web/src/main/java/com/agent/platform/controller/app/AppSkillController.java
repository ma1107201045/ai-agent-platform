package com.agent.platform.controller.app;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.app.AppSkillRollbackDTO;
import com.agent.platform.dao.entity.app.AppAgent;
import com.agent.platform.dao.entity.app.AppSkill;
import com.agent.platform.dao.entity.app.AppSkillVersion;
import com.agent.platform.service.app.AppSkillService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 技能（AppSkill）管理接口。
 *
 * <p>命名遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：
 * <pre>
 *   表 app_skill → 实体 AppSkill → Mapper AppSkillMapper
 *               → Service AppSkillService → 本类 AppSkillController
 *               → URL /api/app/skills（kebab-case 复数）
 * </pre>
 * <p>提供版本快照（versions / rollback）、导入导出，以及引用查询（删除防护）。
 * 技能运行期由应用绑定（app_agent.skill_ids）自动注入，不在此暴露。
 */
@RestController
@RequestMapping("/api/app/skills")
@RequiredArgsConstructor
public class AppSkillController {

    private final AppSkillService skillService;

    // ---------- 技能管理 ----------

    @GetMapping
    public Result<Page<AppSkill>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String type) {
        return Result.ok(skillService.page(page, size, keyword, category, type));
    }

    /** 启用技能列表（应用绑定下拉 / 编排 Agent 节点选择） */
    @GetMapping("/enabled")
    public Result<List<AppSkill>> enabled(@RequestParam(required = false) String category,
                                          @RequestParam(required = false) String type) {
        return Result.ok(skillService.listEnabled(category, type));
    }

    @GetMapping("/{id}")
    public Result<AppSkill> getById(@PathVariable Long id) {
        return Result.ok(skillService.getById(id));
    }

    @PostMapping
    public Result<AppSkill> create(@RequestBody AppSkill skill) {
        return Result.ok(skillService.create(skill));
    }

    @PutMapping("/{id}")
    public Result<AppSkill> update(@PathVariable Long id, @RequestBody AppSkill skill) {
        skill.setId(id);
        return Result.ok(skillService.update(skill));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return Result.ok();
    }

    // ---------- 版本管理 ----------

    @GetMapping("/{id}/versions")
    public Result<List<AppSkillVersion>> versions(@PathVariable Long id) {
        return Result.ok(skillService.versions(id));
    }

    /** 回退到指定历史版本（生成新版本留痕） */
    @PostMapping("/{id}/rollback")
    public Result<AppSkill> rollback(@PathVariable Long id, @RequestBody AppSkillRollbackDTO req) {
        return Result.ok(skillService.rollback(id, req.getVersion()));
    }

    // ---------- 导入导出 ----------

    /** 导出技能（自包含 JSON，可分享给其他租户/工作区） */
    @GetMapping("/{id}/export")
    public Result<AppSkill> exportSkill(@PathVariable Long id) {
        return Result.ok(skillService.exportSkill(id));
    }

    /** 导入技能（code 冲突自动追加后缀） */
    @PostMapping("/import")
    public Result<AppSkill> importSkill(@RequestBody AppSkill skill) {
        return Result.ok(skillService.importSkill(skill));
    }

    // ---------- 引用查询（删除防护） ----------

    /** 引用该技能的应用列表 */
    @GetMapping("/{id}/apps")
    public Result<List<AppAgent>> referencingApps(@PathVariable Long id) {
        return Result.ok(skillService.referencingApps(id));
    }
}
