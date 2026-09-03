package com.agent.platform.dao.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 长期记忆条目：自动抽取或手动沉淀的事实 / 偏好 / 摘要等
 */
@Data
@TableName("mem_item")
public class MemItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 应用ID */
    private Long appId;

    /** 作用域：global 全局 / user 用户 */
    private String scope;

    /** 来源：manual 手动 / auto 自动抽取 */
    private String source;

    /** 类别：preference 偏好 / fact 事实 / event 事件 / summary 摘要 / custom 自定义 */
    private String category;

    /** 记忆内容 */
    private String content;

    /** 重要度 1-5（越大越重要） */
    private Integer importance;

    /** 命中次数（作为上下文注入时累计） */
    private Integer hitCount;

    /** 最近命中时间 */
    private LocalDateTime lastHitAt;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
