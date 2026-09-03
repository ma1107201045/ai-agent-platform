package com.agent.platform.orchestrator.node;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 节点配置字段描述（节点 Schema 单源化）
 * <p>
 * 每个处理器通过 {@link NodeHandler#fields()} 声明自身配置字段，
 * 引擎 / 前端 / 校验器以本描述为<b>唯一事实来源</b>：
 * <ul>
 *   <li>前端 {@code GET /api/orchestrator/node-types} 根据字段自动渲染配置表单；</li>
 *   <li>后端校验（必填 / 类型）与默认值由 schema 驱动，不再散落魔法 key。</li>
 * </ul>
 *
 * <p>字段类型（{@code type}）约定：
 * <table>
 *   <tr><td>text</td><td>单行文本（支持变量引用插入）</td></tr>
 *   <tr><td>textarea / code / password</td><td>多行 / 代码 / 密文输入</td></tr>
 *   <tr><td>number / boolean</td><td>数值 / 开关</td></tr>
 *   <tr><td>select</td><td>下拉（见 {@code options}）</td></tr>
 *   <tr><td>model</td><td>模型下拉（options 动态从模型接口加载）</td></tr>
 *   <tr><td>knowledge</td><td>数据集下拉（动态）</td></tr>
 *   <tr><td>tools / datasets</td><td>工具 / 数据集多选（动态）</td></tr>
 *   <tr><td>json</td><td>JSON 键值/对象编辑</td></tr>
 *   <tr><td>branches</td><td>条件节点分支编辑器（专用复杂控件）</td></tr>
 * </table>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeField {

    /** 配置键（写入节点 config 的 key，与处理器 cfgXxx 读取一致） */
    private String key;

    /** 中文展示名 */
    private String label;

    /** 控件类型，见类注释约定 */
    private String type;

    /** 帮助说明 */
    private String description;

    /** 默认值（前端新建节点时写入 config；类型与 config 反序列化结果一致） */
    @Builder.Default
    private Object defaultValue = null;

    /** 是否必填（后端校验 + 前端必填提示） */
    @Builder.Default
    private boolean required = false;

    /** 输入提示 */
    private String placeholder;

    /** select 下拉选项 */
    @Builder.Default
    private List<Option> options = List.of();

    /** 可选项 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        private String label;
        private String value;
    }

    /** 快捷构造：单选下拉选项 */
    public static Option option(String label, String value) {
        return Option.builder().label(label).value(value).build();
    }

    /** 快捷构造：文本字段 */
    public static NodeField text(String key, String label) {
        return NodeField.builder().key(key).label(label).type("text").build();
    }

    public static NodeField text(String key, String label, Object defaultValue) {
        return NodeField.builder().key(key).label(label).type("text").defaultValue(defaultValue).build();
    }

    /** 快捷构造：多行文本 */
    public static NodeField textarea(String key, String label) {
        return NodeField.builder().key(key).label(label).type("textarea").build();
    }

    /** 快捷构造：代码编辑器 */
    public static NodeField code(String key, String label) {
        return NodeField.builder().key(key).label(label).type("code").build();
    }

    /** 快捷构造：数值 */
    public static NodeField number(String key, String label) {
        return NodeField.builder().key(key).label(label).type("number").build();
    }

    /** 快捷构造：开关 */
    public static NodeField bool(String key, String label) {
        return NodeField.builder().key(key).label(label).type("boolean").build();
    }

    /** 快捷构造：JSON 编辑器 */
    public static NodeField json(String key, String label) {
        return NodeField.builder().key(key).label(label).type("json").build();
    }

    // ==================== 链式方法（工厂返回后继续设置，避免长 builder 样板） ====================

    public NodeField label(String label) {
        this.label = label;
        return this;
    }

    public NodeField description(String description) {
        this.description = description;
        return this;
    }

    public NodeField defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public NodeField required(boolean required) {
        this.required = required;
        return this;
    }

    public NodeField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public NodeField options(List<Option> options) {
        this.options = options;
        return this;
    }
}
