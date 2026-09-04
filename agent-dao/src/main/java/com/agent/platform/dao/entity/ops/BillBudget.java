package com.agent.platform.dao.entity.ops;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用账单-预算设置
 */
@Data
@TableName("bill_budget")
public class BillBudget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 月份 yyyy-MM */
    private String month;

    /** 月度预算(元) */
    private BigDecimal budget;

    /** 超预算提醒: 0否 1是 */
    private Integer notifyEnabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
