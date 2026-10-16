package com.agent.platform.common.script;

import com.agent.platform.common.exception.BizException;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MVEL 脚本沙箱：在执行用户配置的表达式前做静态安全检查。
 * <p>
 * 设计取舍：MVEL2 本身没有内建沙箱，完全隔离需要独立 ClassLoader/进程，
 * 这里采用「字符串字面量剥离 + 危险特征阻断 + new 白名单」的静态检查，
 * 覆盖本平台脚本的典型攻击面（命令执行、反射、IO、线程、类加载），
 * 同时保留日期/集合/数学等常用工具类的实例化能力。
 * <p>
 * 已阻断的能力：{@code Runtime/Process/System/Thread/Class/forName/getClass}、
 * {@code java.io/java.nio/java.net/java.util.concurrent}、反射、{@code import}、
 * 任意类的 {@code new} 实例化（白名单外）。
 */
public final class SafeMvel {

    private SafeMvel() {
    }

    /** 危险特征（在剥离字符串字面量后的脚本上匹配） */
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\bimport\\b|\\bpackage\\b"
                    + "|\\bSystem\\b|\\bRuntime\\b|\\bProcess(?:Builder)?\\b|\\bThread(?:Local)?\\b|\\bExecutors\\b"
                    + "|\\bClass\\b|\\bClassLoader\\b|\\bforName\\b|\\bgetClass\\b|\\bgetDeclared\\w*\\b|\\bMethodHandles?\\b|\\bUnsafe\\b"
                    + "|java\\.io\\.|java\\.nio\\.|java\\.net\\.|java\\.lang\\.reflect|java\\.util\\.concurrent"
                    + "|javax\\.|jdk\\.|(?:com\\.)?sun\\."
                    + "|\\bMVEL\\b|\\.exec\\s*\\(|loadLibrary");

    /** new 表达式中的类名提取 */
    private static final Pattern NEW_CLASS = Pattern.compile("\\bnew\\s+([\\w.$]+)");

    /** 允许实例化的类名前缀（FQCN） */
    private static final List<String> ALLOWED_NEW_PREFIX = List.of(
            "java.util.", "java.time.", "java.math.", "java.text.");

    /** 允许实例化的短名（java.lang 下的常用类） */
    private static final Set<String> ALLOWED_NEW_SHORT = Set.of(
            "StringBuilder", "StringBuffer", "String", "Integer", "Long", "Double", "Boolean");

    /** 编译并检查脚本，返回可重复执行的编译产物 */
    public static Serializable compile(String script) {
        check(script);
        return MVEL.compileExpression(script);
    }

    /** 执行已编译（且已通过检查）的脚本 */
    public static Object execute(Serializable compiled, Map<String, Object> vars) {
        return MVEL.executeExpression(compiled, vars);
    }

    /** 一次性检查并执行 */
    public static Object eval(String script, Map<String, Object> vars) {
        return execute(compile(script), vars);
    }

    /** 静态安全检查：不通过时抛出 BizException */
    public static void check(String script) {
        if (script == null || script.isBlank()) {
            throw new BizException("脚本内容为空");
        }
        String stripped = stripStringLiterals(script);
        Matcher forbidden = FORBIDDEN.matcher(stripped);
        if (forbidden.find()) {
            throw new BizException("脚本包含禁止使用的功能: " + forbidden.group()
                    + "（不允许系统调用、反射、IO、线程与类加载）");
        }
        Matcher news = NEW_CLASS.matcher(stripped);
        while (news.find()) {
            String cls = news.group(1);
            boolean allowed = ALLOWED_NEW_SHORT.contains(cls)
                    || ALLOWED_NEW_PREFIX.stream().anyMatch(cls::startsWith);
            if (!allowed) {
                throw new BizException("脚本不允许实例化该类: " + cls
                        + "（仅允许 java.util/java.time/java.math/java.text 下的常用类）");
            }
        }
    }

    /** 剥离单/双引号字符串字面量，避免字符串内容触发误报 */
    private static String stripStringLiterals(String script) {
        StringBuilder sb = new StringBuilder(script.length());
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '\\' && (inSingle || inDouble)) {
                i++; // 跳过转义字符
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
