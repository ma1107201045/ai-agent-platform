package com.agent.platform.service.datastore;

import com.agent.platform.dao.entity.datastore.DataRecord;
import com.agent.platform.dao.entity.datastore.DataTable;
import com.agent.platform.dao.mapper.datastore.DataRecordMapper;
import com.agent.platform.dao.mapper.datastore.DataTableMapper;
import com.agent.platform.service.datastore.impl.DataStoreServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataStoreCsvTest {
    private final DataTableMapper tableMapper = mock(DataTableMapper.class);
    private final DataRecordMapper recordMapper = mock(DataRecordMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DataStoreServiceImpl service;

    @BeforeEach
    void setUp() {
        DataTable table = new DataTable();
        table.setColumnsJson("""
                [{"key":"text","label":"内容","type":"text"},
                 {"key":"count","label":"数量","type":"number"},
                 {"key":"active","label":"启用","type":"boolean"}]
                """);
        when(tableMapper.selectById(1L)).thenReturn(table);
        service = spy(new DataStoreServiceImpl(tableMapper, recordMapper, objectMapper));
        doAnswer(call -> ((List<?>) call.getArgument(1)).size())
                .when(service).importJson(eq(1L), anyList());
    }

    @Test
    void exportedCsvRoundTripsBomQuotesCommasAndMultilineText() throws Exception {
        String text = "中文,\"引号\"\r\n下一行";
        DataRecord record = new DataRecord();
        record.setDataJson(objectMapper.writeValueAsString(Map.of("text", text, "count", 12, "active", true)));
        when(recordMapper.selectList(any())).thenReturn(List.of(record));

        String csv = service.exportCsv(1L);
        assertTrue(csv.startsWith("\uFEFF内容,数量,启用\r\n"));
        assertTrue(csv.contains("\"中文,\"\"引号\"\"\r\n下一行\""));
        assertTrue(csv.endsWith("\r\n"));
        assertEquals(1, service.importCsv(1L, file(csv)));
        Map<String, Object> imported = importedRows().get(0);
        assertEquals(text, imported.get("text"));
        assertEquals(12, ((Number) imported.get("count")).intValue());
        assertEquals(true, imported.get("active"));
    }

    @Test
    void importAcceptsReorderedKeysBlankLinesAndNoFinalNewline() throws Exception {
        assertEquals(1, service.importCsv(1L, file("active,text,count\r\n\r\nyes,hello,42")));
        Map<String, Object> row = importedRows().get(0);
        assertEquals("hello", row.get("text"));
        assertEquals(42, ((Number) row.get("count")).intValue());
        assertEquals(true, row.get("active"));
    }

    @Test
    void malformedQuotedFieldDoesNotImportAnyRows() throws Exception {
        assertThrows(IOException.class, () -> service.importCsv(1L, file("text\n\"unterminated")));
        verify(service, never()).importJson(anyLong(), anyList());
    }

    @Test
    void exportPreservesNullAndMissingValuesAsEmptyFields() {
        DataRecord record = new DataRecord();
        record.setDataJson("{\"text\":null}");
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        assertEquals("\uFEFF内容,数量,启用\r\n\"\",,\r\n", service.exportCsv(1L));
    }

    private MultipartFile file(String csv) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        return file;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> importedRows() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(service).importJson(eq(1L), captor.capture());
        return captor.getValue();
    }
}
