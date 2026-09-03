package com.agent.platform.dao.dto.datastore;

import lombok.Data;

import java.util.Map;

/**
 * 创建 / 更新数据行记录入参
 */
@Data
public class DataRecordReqDTO {
    /** 列键值对象 */
    private Map<String, Object> data;
}
