package com.agent.platform.dao.entity.guard;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容安全-规则库
 */
@Data
@TableName("guard_rule")
public class GuardRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String name;

    private String description;

    /** 作用方向: input输入/output输出 */
    private String direction;

    /** 匹配方式: keyword关键词/regex正则/prompt_injection注入检测 */
    private String matchType;

    /** 匹配内容(关键词逗号分隔 或 正则表达式) */
    private String ruleContent;

    /** 处置动作: block拦截/mask打码/replace替换 */
    private String action;

    /** 替换/打码文本 */
    private String replaceText;

    /** 风险等级 1-5 */
    private Integer riskLevel;

    /** 启用: 0否 1是 */
    private Integer enabled;

    /** 执行优先级(数字小优先) */
    private Integer priority;

    /** 累计命中次数 */
    private Long hitCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
