package com.agent.platform.service.datastore;

import com.agent.platform.common.exception.BizException;
import com.agent.platform.dao.entity.datastore.DataRecord;
import com.agent.platform.dao.entity.datastore.DataTable;
import com.agent.platform.dao.mapper.datastore.DataRecordMapper;
import com.agent.platform.dao.mapper.datastore.DataTableMapper;
import com.agent.platform.dao.dto.datastore.DataColumnDefDTO;
import com.agent.platform.dao.vo.datastore.DataRecordVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据存储服务：自定义数据表（建表/列定义）+ 行记录 CRUD + JSON/CSV 导入导出。
 *
 * <p>表结构是“逻辑表”：列定义存于 {@link DataTable#getColumnsJson()}，
 * 行记录以 JSON 对象存于 data_record，适合平台内维护结构化业务数据供智能体使用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStoreService {

    private static final Set<String> COLUMN_TYPES = Set.of("text", "number", "boolean", "date", "select");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private final DataTableMapper tableMapper;
    private final DataRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    // ---------- 数据表 ----------

    public Page<DataTable> pageTables(long page, long size, String keyword) {
        LambdaQueryWrapper<DataTable> wrapper = new LambdaQueryWrapper<DataTable>()
                .eq(DataTable::getStatus, 1)
                .orderByDesc(DataTable::getUpdateTime);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(DataTable::getName, kw)
                    .or().like(DataTable::getLabel, kw)
                    .or().like(DataTable::getDescription, kw));
        }
        return tableMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public DataTable getTable(Long id) {
        DataTable table = tableMapper.selectById(id);
        if (table == null) {
            throw new BizException("数据表不存在: " + id);
        }
        return table;
    }

    public DataTable createTable(String name, String label, String description, List<DataColumnDefDTO> columns) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("请输入数据表名称");
        }
        String trimName = name.trim();
        ensureNameUnique(trimName, null);
        validateColumns(columns);
        LocalDateTime now = LocalDateTime.now();
        DataTable table = new DataTable();
        table.setTenantId(1L);
        table.setName(trimName);
        table.setLabel(StringUtils.hasText(label) ? label.trim() : trimName);
        table.setDescription(description);
        table.setColumnsJson(toColumnsJson(columns));
        table.setRowCount(0);
        table.setStatus(1);
        table.setCreateTime(now);
        table.setUpdateTime(now);
        tableMapper.insert(table);
        return table;
    }

    public DataTable updateTable(Long id, String name, String label, String description,
                                 List<DataColumnDefDTO> columns, Integer status) {
        DataTable table = getTable(id);
        if (name != null && StringUtils.hasText(name)) {
            String trimName = name.trim();
            ensureNameUnique(trimName, id);
            table.setName(trimName);
            if (table.getLabel() == null || !StringUtils.hasText(table.getLabel())) {
                table.setLabel(trimName);
            }
        }
        if (label != null) {
            table.setLabel(StringUtils.hasText(label) ? label.trim() : table.getName());
        }
        if (description != null) {
            table.setDescription(description);
        }
        if (columns != null) {
            validateColumns(columns);
            table.setColumnsJson(toColumnsJson(columns));
        }
        if (status != null) {
            table.setStatus(status);
        }
        table.setUpdateTime(LocalDateTime.now());
        tableMapper.updateById(table);
        return table;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTable(Long id) {
        getTable(id);
        recordMapper.delete(new LambdaQueryWrapper<DataRecord>().eq(DataRecord::getTableId, id));
        tableMapper.deleteById(id);
    }

    // ---------- 行记录 ----------

    /** 分页返回行记录（data 为列键值对象，便于前端直接渲染） */
    public Page<DataRecordVO> pageRecords(Long tableId, long page, long size, String keyword) {
        DataTable table = getTable(tableId);
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        LambdaQueryWrapper<DataRecord> wrapper = new LambdaQueryWrapper<DataRecord>()
                .eq(DataRecord::getTableId, tableId)
                .orderByDesc(DataRecord::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(DataRecord::getDataJson, keyword.trim());
        }
        Page<DataRecord> raw = recordMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DataRecordVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<DataRecordVO> views = new ArrayList<>();
        for (DataRecord r : raw.getRecords()) {
            views.add(toView(r));
        }
        result.setRecords(views);
        return result;
    }

    public DataRecordVO getRecord(Long id) {
        DataRecord record = requireRecord(id);
        return toView(record);
    }

    public DataRecordVO createRecord(Long tableId, Map<String, Object> data) {
        DataTable table = getTable(tableId);
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        Map<String, Object> row = normalizeRow(columns, data);
        LocalDateTime now = LocalDateTime.now();
        DataRecord record = new DataRecord();
        record.setTableId(tableId);
        record.setDataJson(toDataJson(row));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        recordMapper.insert(record);
        table.setRowCount(table.getRowCount() == null ? 1 : table.getRowCount() + 1);
        table.setUpdateTime(now);
        tableMapper.updateById(table);
        return toView(record);
    }

    public DataRecordVO updateRecord(Long id, Map<String, Object> data) {
        DataRecord record = requireRecord(id);
        DataTable table = getTable(record.getTableId());
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        Map<String, Object> row = normalizeRow(columns, data);
        record.setDataJson(toDataJson(row));
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        return toView(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRecord(Long id) {
        DataRecord record = requireRecord(id);
        recordMapper.deleteById(id);
        DataTable table = tableMapper.selectById(record.getTableId());
        if (table != null) {
            table.setRowCount(Math.max(0, (table.getRowCount() == null ? 0 : table.getRowCount()) - 1));
            table.setUpdateTime(LocalDateTime.now());
            tableMapper.updateById(table);
        }
    }

    // ---------- 导入导出 ----------

    /** JSON 数组导入（[{列key: 值}, ...]） */
    @Transactional(rollbackFor = Exception.class)
    public int importJson(Long tableId, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BizException("没有可导入的数据");
        }
        DataTable table = getTable(tableId);
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            try {
                Map<String, Object> row = normalizeRow(columns, rows.get(i));
                DataRecord record = new DataRecord();
                record.setTableId(tableId);
                record.setDataJson(toDataJson(row));
                record.setCreateTime(now);
                record.setUpdateTime(now);
                recordMapper.insert(record);
                count++;
            } catch (BizException e) {
                throw new BizException("第 " + (i + 1) + " 行数据有误: " + e.getMessage());
            }
        }
        table.setRowCount((table.getRowCount() == null ? 0 : table.getRowCount()) + count);
        table.setUpdateTime(now);
        tableMapper.updateById(table);
        return count;
    }

    /** CSV 导入：首行须为表头（列名或列 key），列顺序任意、列名匹配取第一个数据值 */
    @Transactional(rollbackFor = Exception.class)
    public int importCsv(Long tableId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要导入的 CSV 文件");
        }
        DataTable table = getTable(tableId);
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        List<List<String>> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            lines = readCsv(reader);
        }
        if (lines.size() < 2) {
            throw new BizException("CSV 至少需要 1 行表头和 1 行数据");
        }
        List<String> header = lines.get(0);
        int[] colIdx = new int[header.size()];
        for (int i = 0; i < header.size(); i++) {
            colIdx[i] = matchColumn(columns, header.get(i));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = 1; r < lines.size(); r++) {
            List<String> line = lines.get(r);
            if (line.size() == 1 && line.get(0).isBlank()) {
                continue; // 跳过空行
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                int idx = colIdx[i];
                if (idx >= 0) {
                    String raw = i < line.size() ? line.get(i) : null;
                    row.put(columns.get(idx).getKey(), coerceValue(columns.get(idx), raw));
                }
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new BizException("CSV 中没有有效数据行");
        }
        return importJson(tableId, rows);
    }

    /** CSV 导出：返回带 UTF-8 BOM 的文本，含表头（列名） */
    public String exportCsv(Long tableId) {
        DataTable table = getTable(tableId);
        List<DataColumnDefDTO> columns = parseColumns(table.getColumnsJson());
        List<DataRecord> records = recordMapper.selectList(new LambdaQueryWrapper<DataRecord>()
                .eq(DataRecord::getTableId, tableId)
                .orderByAsc(DataRecord::getId));
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // Excel 中文兼容 BOM
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvField(columns.get(i).getLabel() == null || columns.get(i).getLabel().isBlank()
                    ? columns.get(i).getKey() : columns.get(i).getLabel()));
        }
        sb.append("\r\n");
        for (DataRecord record : records) {
            Map<String, Object> row;
            try {
                row = objectMapper.readValue(record.getDataJson(), new TypeReference<Map<String, Object>>() {});
            } catch (IOException e) {
                log.warn("解析行数据失败 id={}: {}", record.getId(), e.getMessage());
                continue;
            }
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sb.append(',');
                Object value = row.get(columns.get(i).getKey());
                sb.append(csvField(value == null ? "" : String.valueOf(value)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    public List<DataColumnDefDTO> parseColumns(String columnsJson) {
        if (columnsJson == null || columnsJson.isBlank()) {
            throw new BizException("数据表尚未定义列");
        }
        try {
            List<DataColumnDefDTO> columns = objectMapper.readValue(columnsJson, new TypeReference<List<DataColumnDefDTO>>() {});
            if (columns == null || columns.isEmpty()) {
                throw new BizException("数据表尚未定义列");
            }
            return columns;
        } catch (IOException e) {
            throw new BizException("列定义数据损坏: " + e.getMessage());
        }
    }

    // ---------- 私有工具 ----------

    private void ensureNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<DataTable> wrapper = new LambdaQueryWrapper<DataTable>()
                .eq(DataTable::getName, name)
                .eq(DataTable::getStatus, 1)
                .ne(excludeId != null, DataTable::getId, excludeId);
        if (tableMapper.selectCount(wrapper) > 0) {
            throw new BizException("已存在同名数据表: " + name);
        }
    }

    private void validateColumns(List<DataColumnDefDTO> columns) {
        if (columns == null || columns.isEmpty()) {
            throw new BizException("至少需要定义一个列");
        }
        Set<String> keys = new HashSet<>();
        for (DataColumnDefDTO col : columns) {
            if (col == null || !StringUtils.hasText(col.getKey())) {
                throw new BizException("列的 key 不能为空");
            }
            String key = col.getKey().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BizException("列的 key 须为字母/数字/下划线且以字母或下划线开头: " + key);
            }
            if (!keys.add(key)) {
                throw new BizException("列的 key 重复: " + key);
            }
            if (col.getType() == null || !COLUMN_TYPES.contains(col.getType())) {
                throw new BizException("不支持的列类型: " + (col.getType() == null ? "" : col.getType()));
            }
        }
    }

    private String toColumnsJson(List<DataColumnDefDTO> columns) {
        try {
            return objectMapper.writeValueAsString(columns);
        } catch (JsonProcessingException e) {
            throw new BizException("列定义序列化失败: " + e.getMessage());
        }
    }

    private String toDataJson(Map<String, Object> row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            throw new BizException("行数据序列化失败: " + e.getMessage());
        }
    }

    private Map<String, Object> normalizeRow(List<DataColumnDefDTO> columns, Map<String, Object> data) {
        if (data == null) {
            throw new BizException("行数据不能为空");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (DataColumnDefDTO col : columns) {
            if (!data.containsKey(col.getKey())) {
                continue; // 缺列时按空处理，方便导入模板不全的场景
            }
            row.put(col.getKey(), coerceValue(col, data.get(col.getKey())));
        }
        return row;
    }

    private Object coerceValue(DataColumnDefDTO col, Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s && s.isBlank()) {
            return null;
        }
        switch (col.getType()) {
            case "number":
                if (raw instanceof Number) {
                    return raw;
                }
                try {
                    String text = String.valueOf(raw).trim();
                    return text.contains(".") ? Double.parseDouble(text) : Long.parseLong(text);
                } catch (NumberFormatException e) {
                    throw new BizException("列[" + col.getKey() + "]需要数字类型");
                }
            case "boolean":
                if (raw instanceof Boolean) {
                    return raw;
                }
                String boolText = String.valueOf(raw).trim().toLowerCase();
                if (boolText.equals("true") || boolText.equals("1") || boolText.equals("是") || boolText.equals("yes")) {
                    return true;
                }
                if (boolText.equals("false") || boolText.equals("0") || boolText.equals("否") || boolText.equals("no")) {
                    return false;
                }
                throw new BizException("列[" + col.getKey() + "]需要布尔类型");
            default:
                return String.valueOf(raw);
        }
    }

    private int matchColumn(List<DataColumnDefDTO> columns, String header) {
        if (header == null) {
            return -1;
        }
        String text = header.trim();
        for (int i = 0; i < columns.size(); i++) {
            DataColumnDefDTO col = columns.get(i);
            if (text.equals(col.getKey()) || text.equals(col.getLabel())) {
                return i;
            }
        }
        return -1;
    }

    private DataRecordVO toView(DataRecord record) {
        Map<String, Object> data;
        try {
            data = objectMapper.readValue(record.getDataJson(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            data = new HashMap<>();
            data.put("_error", "行数据解析失败");
        }
        return new DataRecordVO(record.getId(), record.getTableId(), data, record.getCreateTime(), record.getUpdateTime());
    }

    private DataRecord requireRecord(Long id) {
        DataRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("数据记录不存在: " + id);
        }
        return record;
    }

    /** 简易 RFC4180 CSV 解析：支持引号包裹字段与字段内逗号/引号（"" 转义） */
    private List<List<String>> readCsv(BufferedReader reader) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuote = false;
        int c;
        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (inQuote) {
                if (ch == '"') {
                    int next = reader.read();
                    if (next == -1) {
                        inQuote = false;
                    } else if (next == '"') {
                        cell.append('"');
                    } else {
                        inQuote = false;
                        // 引号结束后的普通字符（逗号/换行）按行外逻辑处理
                        if (consumeCsvChar((char) next, row, cell)) {
                            rows.add(row);
                            row = new ArrayList<>();
                        }
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                inQuote = true;
            } else if (consumeCsvChar(ch, row, cell)) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        // 收尾：无换行结尾的最后一个单元格
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            cell.setLength(0);
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    /** 处理 CSV 行外普通字符；返回 true 表示已结束一行（换行） */
    private boolean consumeCsvChar(char ch, List<String> row, StringBuilder cell) {
        if (ch == ',') {
            row.add(cell.toString());
            cell.setLength(0);
        } else if (ch == '\n') {
            row.add(cell.toString());
            cell.setLength(0);
            return true;
        } else if (ch != '\r') {
            cell.append(ch);
        }
        return false;
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
