package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 技能定义（可复用能力包，应用 -> 技能）
 * <p>
 * 技能 = 指令正文 + 变量 + 依赖工具 + 参考数据集，可被多个智能体/工作流引用复用。
 * 类型：instruction 指令型 / tool 工具复合型 / knowledge 知识技能型。
 * 内容变更留痕于 {@link AppSkillVersion}（版本策略与提示词模板一致）。
 */
@Data
@TableName("app_skill")
public class AppSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 技能名称 */
    private String name;

    /** 技能唯一标识（小写 kebab-case，租户内唯一） */
    private String code;

    /** 技能描述（何时使用该技能，供运行期按需匹配） */
    private String description;

    /** 分类：general 通用 / system 系统 / business 业务 / custom 自定义 */
    private String category;

    /** 类型：instruction 指令型 / tool 工具复合型 / knowledge 知识技能型 */
    private String type;

    /** 图标 */
    private String icon;

    /** 技能指令正文（Markdown，支持 {{var}} 占位） */
    private String content;

    /** 变量定义（JSON 数组：[{"name":"var","desc":"说明"}]） */
    private String variables;

    /** 依赖工具 ID 列表（JSON 数组字符串），tool 类型使用 */
    private String toolIds;

    /** 参考数据集 ID 列表（JSON 数组字符串），knowledge 类型使用 */
    private String datasetIds;

    /** 当前版本号 */
    private Integer version;

    /** 状态：0禁用 1启用 */
    private Integer status;

    /** 创建人 */
    private Long createdBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0正常 1回收站 */
    @TableLogic
    private Integer deleted;

    /** 删除时间 */
    private LocalDateTime deletedTime;
}
