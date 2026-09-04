package com.agent.platform.dao.entity.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用市场条目
 */
@Data
@TableName("app_market_item")
public class AppMarketItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上架租户(空/0=平台官方) */
    private Long tenantId;

    private String name;

    private String description;

    /** 分类: general/customer_service/translate/writing/office/analysis/other */
    private String category;

    private String icon;

    /** 应用类型: chatflow/workflow/agent */
    private String type;

    /** 应用DSL快照(安装时拷贝) */
    private String workflowJson;

    /** 基础配置(JSON: welcome_message等) */
    private String configJson;

    /** 来源应用ID(用户上架时) */
    private Long sourceAppId;

    private String author;

    private Integer installCount;

    /** 状态: 0下架 1上架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
