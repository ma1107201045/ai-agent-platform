package com.agent.platform.controller.datastore;

import com.agent.platform.common.result.Result;
import com.agent.platform.dao.dto.datastore.DataRecordReqDTO;
import com.agent.platform.dao.dto.datastore.DataTableReqDTO;
import com.agent.platform.dao.entity.datastore.DataTable;
import com.agent.platform.dao.vo.datastore.DataRecordVO;
import com.agent.platform.service.datastore.DataStoreService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 数据存储接口：自定义数据表 + 行记录 + JSON/CSV 导入导出
 *
 * <p>URL：/api/data-store/*</p>
 */
@RestController
@RequestMapping("/api/data-store")
@RequiredArgsConstructor
public class DataStoreController {

    private final DataStoreService dataStoreService;

    // ---------- 数据表 ----------

    @GetMapping("/tables")
    public Result<Page<DataTable>> tablePage(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "20") long size,
                                             @RequestParam(required = false) String keyword) {
        return Result.ok(dataStoreService.pageTables(page, size, keyword));
    }

    @GetMapping("/tables/{id}")
    public Result<DataTable> getTable(@PathVariable Long id) {
        return Result.ok(dataStoreService.getTable(id));
    }

    @PostMapping("/tables")
    public Result<DataTable> createTable(@RequestBody DataTableReqDTO req) {
        return Result.ok(dataStoreService.createTable(req.getName(), req.getLabel(),
                req.getDescription(), req.getColumns()));
    }

    @PutMapping("/tables/{id}")
    public Result<DataTable> updateTable(@PathVariable Long id, @RequestBody DataTableReqDTO req) {
        return Result.ok(dataStoreService.updateTable(id, req.getName(), req.getLabel(),
                req.getDescription(), req.getColumns(), req.getStatus()));
    }

    @DeleteMapping("/tables/{id}")
    public Result<Void> deleteTable(@PathVariable Long id) {
        dataStoreService.deleteTable(id);
        return Result.ok();
    }

    // ---------- 行记录 ----------

    @GetMapping("/tables/{id}/records")
    public Result<Page<DataRecordVO>> recordPage(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String keyword) {
        return Result.ok(dataStoreService.pageRecords(id, page, size, keyword));
    }

    @PostMapping("/tables/{id}/records")
    public Result<DataRecordVO> createRecord(@PathVariable Long id, @RequestBody DataRecordReqDTO req) {
        return Result.ok(dataStoreService.createRecord(id, req.getData()));
    }

    @PutMapping("/records/{recordId}")
    public Result<DataRecordVO> updateRecord(@PathVariable Long recordId, @RequestBody DataRecordReqDTO req) {
        return Result.ok(dataStoreService.updateRecord(recordId, req.getData()));
    }

    @DeleteMapping("/records/{recordId}")
    public Result<Void> deleteRecord(@PathVariable Long recordId) {
        dataStoreService.deleteRecord(recordId);
        return Result.ok();
    }

    // ---------- 导入导出 ----------

    /** JSON 数组导入：[{列key: 值}, ...] */
    @PostMapping("/tables/{id}/import-json")
    public Result<Integer> importJson(@PathVariable Long id, @RequestBody List<Map<String, Object>> rows) {
        return Result.ok(dataStoreService.importJson(id, rows));
    }

    /** CSV 文件导入：首行为表头（列名或列 key） */
    @PostMapping(value = "/tables/{id}/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Integer> importCsv(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(dataStoreService.importCsv(id, file));
    }

    /** 导出为 CSV（UTF-8 BOM），触发浏览器下载 */
    @GetMapping("/tables/{id}/export-csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long id) {
        String csv = dataStoreService.exportCsv(id);
        DataTable table = dataStoreService.getTable(id);
        String filename = table.getName() + ".csv";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }
}
