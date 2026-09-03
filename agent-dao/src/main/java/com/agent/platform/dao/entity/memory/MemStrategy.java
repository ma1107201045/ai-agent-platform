package com.agent.platform.dao.entity.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆策略：每个智能体应用一条，控制长期记忆的开关 / 抽取 / 注入参数
 */
@Data
@TableName("mem_strategy")
public class MemStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 应用ID */
    private Long appId;

    /** 是否启用长期记忆：0否 1是 */
    private Integer enabled;

    /** 对话后自动抽取记忆：0否 1是 */
    private Integer autoExtract;

    /** 自动抽取使用的对话模型ID */
    private Long extractModelId;

    /** 每次对话注入的记忆条目数 */
    private Integer topN;

    /** 记忆保留天数（空 = 永久保留） */
    private Integer keepDays;

    /** 单应用记忆条目上限 */
    private Integer maxItems;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
