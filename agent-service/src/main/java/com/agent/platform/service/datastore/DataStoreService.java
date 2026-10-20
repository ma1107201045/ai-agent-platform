package com.agent.platform.service.datastore;

import com.agent.platform.dao.entity.datastore.DataTable;
import com.agent.platform.dao.dto.datastore.DataColumnDefDTO;
import com.agent.platform.dao.vo.datastore.DataRecordVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 数据存储服务：自定义数据表（建表/列定义）+ 行记录 CRUD + JSON/CSV 导入导出。
 *
 * <p>表结构是“逻辑表”：列定义存于 {@link DataTable#getColumnsJson()}，
 * 行记录以 JSON 对象存于 data_record，适合平台内维护结构化业务数据供智能体使用。</p>
 */
public interface DataStoreService {

    public Page<DataTable> pageTables(long page, long size, String keyword);

    public DataTable getTable(Long id);

    public DataTable createTable(String name, String label, String description, List<DataColumnDefDTO> columns);

    public DataTable updateTable(Long id, String name, String label, String description,
                                 List<DataColumnDefDTO> columns, Integer status);

    public void deleteTable(Long id);

    /** 分页返回行记录（data 为列键值对象，便于前端直接渲染） */
    public Page<DataRecordVO> pageRecords(Long tableId, long page, long size, String keyword);

    public DataRecordVO getRecord(Long id);

    public DataRecordVO createRecord(Long tableId, Map<String, Object> data);

    public DataRecordVO updateRecord(Long id, Map<String, Object> data);

    public void deleteRecord(Long id);

    /** 批量删除行记录（单次 ≤500），返回实际删除行数 */
    public int deleteRecordBatch(List<Long> ids);

    /** JSON 数组导入（[{列key: 值}, ...]） */
    public int importJson(Long tableId, List<Map<String, Object>> rows);

    /** CSV 导入：首行须为表头（列名或列 key），列顺序任意、列名匹配取第一个数据值 */
    public int importCsv(Long tableId, MultipartFile file) throws IOException;

    /** CSV 导出：返回带 UTF-8 BOM 的文本，含表头（列名） */
    public String exportCsv(Long tableId);

    public List<DataColumnDefDTO> parseColumns(String columnsJson);
}
