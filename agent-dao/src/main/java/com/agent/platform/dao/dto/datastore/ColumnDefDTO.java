package com.agent.platform.dao.dto.datastore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据表列定义（与 data_table.columns_json 对应）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnDefDTO {
    private String key;
    private String label;
    /** text/number/boolean/date/select */
    private String type;
    /** select 类型的选项 */
    private List<String> options;
}
