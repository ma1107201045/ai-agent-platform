package com.agent.platform;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

/**
 * 智能体平台启动类
 */
@SpringBootTest
public class AgentPlatformApplicationTests {


    @Test
    void test01() throws IOException, TikaException {
        Tika tika = new Tika();
        //resources 目录 下边有个 test.txt 文件
        File file = new File("D:\\workspace\\ai-agent-platform\\agent-web\\" +
                "src\\test\\resources\\出口退税全流程办理指引报告.pdf");

        // 自动识别真实文件类型
        String type = tika.detect(file);
        System.out.println("类型: " + type);

        // 提取纯文本
        String text = tika.parseToString(file);
        System.out.println(text);
    }
}
