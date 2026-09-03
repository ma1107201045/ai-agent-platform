package com.agent.platform.dao.entity.datastore;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自定义数据表：列定义以 JSON 存储（[{key,label,type,options?}]），行数据存于 data_record
 */
@Data
@TableName("data_table")
public class DataTable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 数据表名（展示用，租户内唯一） */
    private String name;

    /** 显示名称 / 别名 */
    private String label;

    private String description;

    /** 列定义（JSON 数组：[{"key","label","type","options?"}]） */
    private String columnsJson;

    /** 行记录数 */
    private Integer rowCount;

    /** 状态：0禁用 1启用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
