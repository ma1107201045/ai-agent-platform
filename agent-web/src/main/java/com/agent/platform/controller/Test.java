package com.agent.platform.controller;

import cn.idev.excel.annotation.ExcelProperty;
import cn.idev.excel.converters.longconverter.LongStringConverter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/test")
public class Test {


    @GetMapping("/export-excel")
    public void exportOrderExcel(HttpServletResponse response) throws IOException {
        List<TestVO> list = List.of(new TestVO(2115004315838087170L));
        ExcelUtils.write(response, "回收订单.xls", "数据", TestVO.class, list);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class TestVO {

        @ExcelProperty(value = "订单ID")
        private Long id;

    }
}


