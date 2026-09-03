package com.agent.platform.dao.vo.datastore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据行记录视图：data 为可直接渲染的列键值对象
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataRecordVO {
    private Long id;
    private Long tableId;
    private Map<String, Object> data;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
