package com.agent.platform.dao.dto.datastore;

import lombok.Data;

import java.util.List;

/**
 * 创建 / 更新数据表入参
 */
@Data
public class DataTableReqDTO {
    private String name;
    private String label;
    private String description;
    private List<ColumnDefDTO> columns;
    /** 更新时的目标状态（创建时忽略） */
    private Integer status;
}
