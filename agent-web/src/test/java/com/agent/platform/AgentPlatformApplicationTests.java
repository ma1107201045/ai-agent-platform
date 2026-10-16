package com.agent.platform;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 智能体平台启动类
 */
@SpringBootTest
public class AgentPlatformApplicationTests {

    @Test
    void testTikaPdf() throws IOException, TikaException {
        Tika tika = new Tika();
        // 从 classpath 加载测试资源，避免硬编码绝对路径
        ClassPathResource resource = new ClassPathResource("出口退税全流程办理指引报告.pdf");
        try (InputStream in = resource.getInputStream()) {
            // 自动识别真实文件类型
            String type = tika.detect(resource.getFile());
            System.out.println("类型: " + type);
            assertEquals("application/pdf", type);

            // 提取纯文本
            String text = tika.parseToString(in);
            assertFalse(text == null || text.isBlank(), "PDF 文本提取结果为空");
            System.out.println(text);
        }
    }
}
