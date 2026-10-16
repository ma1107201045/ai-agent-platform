package com.agent.platform.common.script;

import com.agent.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafeMvel 沙箱静态检查测试
 */
class SafeMvelTest {

    private Map<String, Object> vars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("input", "hello world");
        return vars;
    }

    // ---------- 合法脚本放行 ----------

    @Test
    void allowsPlainStringAndMath() {
        Object result = SafeMvel.eval("return input.length() * 2", vars());
        assertEquals(22, result);
    }

    @Test
    void allowsSeedToolStyleScript() {
        // 种子数据 text_stats 工具同款写法
        String code = "var t = input != null ? String.valueOf(input) : \"\"; "
                + "return \"字符数=\" + t.length()";
        Object result = SafeMvel.eval(code, vars());
        assertEquals("字符数=11", result);
    }

    @Test
    void allowsWhitelistedNew() {
        // 种子数据中的日期工具写法
        assertDoesNotThrow(() -> SafeMvel.check(
                "var fmt = new java.text.SimpleDateFormat(\"yyyy-MM-dd\"); return fmt.format(new java.util.Date())"));
        assertDoesNotThrow(() -> SafeMvel.check("return new java.math.BigDecimal(\"1.5\")"));
        assertDoesNotThrow(() -> SafeMvel.check("var list = new java.util.ArrayList(); return list"));
    }

    @Test
    void stringLiteralContainingDangerousWordIsAllowed() {
        // 字符串字面量内的敏感词不触发误报
        Object result = SafeMvel.eval("return \"System.exit 这个词在字符串里没事\"", vars());
        assertTrue(String.valueOf(result).contains("System.exit"));
    }

    // ---------- 危险脚本拦截 ----------

    @Test
    void blocksRuntimeExec() {
        assertThrows(BizException.class, () ->
                SafeMvel.check("Runtime.getRuntime().exec(\"calc\")"));
    }

    @Test
    void blocksSystemExit() {
        assertThrows(BizException.class, () -> SafeMvel.check("System.exit(0)"));
    }

    @Test
    void blocksArbitraryNew() {
        assertThrows(BizException.class, () -> SafeMvel.check("new java.io.File(\"/etc/passwd\")"));
        assertThrows(BizException.class, () -> SafeMvel.check("new com.agent.platform.common.security.JwtUtil()"));
    }

    @Test
    void blocksReflectionAndClassLoading() {
        assertThrows(BizException.class, () -> SafeMvel.check("Class.forName(\"java.lang.Runtime\")"));
        assertThrows(BizException.class, () -> SafeMvel.check("input.getClass()"));
        assertThrows(BizException.class, () -> SafeMvel.check("java.lang.reflect.Method m"));
    }

    @Test
    void blocksThreadsAndConcurrency() {
        assertThrows(BizException.class, () -> SafeMvel.check("Thread.sleep(1000)"));
        assertThrows(BizException.class, () -> SafeMvel.check("new java.util.concurrent.ConcurrentHashMap()"));
    }

    @Test
    void blocksImportAndMvelSelf() {
        assertThrows(BizException.class, () -> SafeMvel.check("import java.io.File; return 1"));
        assertThrows(BizException.class, () -> SafeMvel.check("MVEL.eval(\"1\")"));
    }

    @Test
    void blocksBlankScript() {
        assertThrows(BizException.class, () -> SafeMvel.check("  "));
    }
}
