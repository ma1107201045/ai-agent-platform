package com.agent.platform.dao.entity.datastore;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据记录：行数据以 JSON 对象存储（键为列 key）
 */
@Data
@TableName("data_record")
public class DataRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据表ID */
    private Long tableId;

    /** 行数据（JSON 对象：{列key: 值}） */
    private String dataJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
