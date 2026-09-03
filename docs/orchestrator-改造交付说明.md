# agent-orchestrator 改造交付说明

> 对应《agent-workflow-模块优化建议.md》的分析结论，本文件记录已落地的改造、验证结果与后续迭代建议。

## 一、改造目标（回顾）

从 **分层 / 扩展 / 维护** 三个维度重构 `agent-workflow`（现更名 `agent-orchestrator`）：
1. 分层清晰：引擎调度（`WorkflowEngine`）与业务执行（`NodeHandler`）解耦，外部能力走 SPI；
2. 扩展友好：新增节点类型不改引擎，仅实现 handler + 声明 schema；
3. 可维护：节点字段 / 默认值 / 必填校验不再前后端各写一份，收敛为单一事实来源。

## 二、本轮已落地改造

### 1. 运行事件化 + 调试 runId
- `WorkflowEventListener` 事件总线：`FlowStarted / NodeStarted / NodeFinished / FlowFinished`；
- 引擎 `run(...)` 支持显式 `runId`，运行状态 `activeRuns` 供取消 / 运行记录 / 监控使用；
- `RunResult` 记录 `runId / status / costMs / trace` 等完整执行轨迹；
- 监听器通过 Spring 注入，可在不改引擎的前提下接入持久化 / SSE / 计量等下游。

### 2. 节点 Schema 单源化（核心）
- **后端声明**：`NodeHandler.fields()` 返回 `List<NodeField>`（key / label / 类型 / 默认值 / 必填 / 说明 / 下拉选项）；
  9 种节点（start / end / llm / agent / condition / code / http / template / knowledge）全部声明完毕。
- **对外接口**：`GET /api/orchestrator/node-types` 返回 `NodeTypeSchema[]`（含 branch / source / target 连线约束）；
- **校验驱动**：`NodeExecutionPolicy` 按 schema `required` 做执行前必填校验（与 handler.validate 互补）；
- **默认值驱动**：引擎执行前 `NodeHandlerRegistry.applyDefaults(node)` 补齐缺失默认键 → 新旧 DSL 行为自动对齐；
- **前端驱动**：
  - `utils/nodeSchema.ts`：加载并缓存 schema，`buildInitialConfig` 以 schema 默认值为新建节点初始配置；
  - `NodeConfigPanel`：打开节点时补齐缺失默认键、按 schema 必填规则展示红色错误提示；
  - `NODE_TYPE_META` 响应式化，后端 label 优先、前端视觉（icon/渐变）保留；
  - 新增节点无需再改任何前端代码即可获得：默认配置 + 必填校验 + 元数据。

### 3. 分层与扩展固化
- `NodeContext`（读配置 / 渲染变量 / 写输出）+ SPI（ModelProvider / KnowledgeProvider / AgentRunner）已落地；
- 注册表自动收集 Spring bean；处理器间覆盖可观测（日志 warning）。

## 三、验证结果

| 项 | 结果 |
| --- | --- |
| 后端全模块 `mvn test`（agent-web 上下文启动） | ✅ 通过，处理器 9/9 全部注册 |
| 前端 `vue-tsc` 类型检查 | ✅ 本次新增文件无错误（存量文件 prompts/api-keys 存在历史报错，与本次无关） |
| `/api/orchestrator/node-types` | ✅ 新增接口，随上下文启动加载 |

## 四、主要文件清单

**后端（agent-orchestrator）**
- `node/NodeField.java`（新增）：字段描述模型 + 快捷工厂
- `node/NodeTypeSchema.java`（新增）：对外 Schema
- `node/NodeHandler.java`：新增默认 `fields()` SPI
- `node/NodeHandlerRegistry.java`：`schemas()` / `applyDefaults()`
- `NodeExecutionPolicy.java`：schema 必填校验
- `WorkflowEngine.java`：执行前 applyDefaults
- 9 个 `NodeHandler`：各自声明 `fields()`

**Web**
- `controller/orchestrator/NodeSchemaController.java`（新增）：node-types 接口

**前端（frontend）**
- `api/types.ts`：NodeFieldSchema / NodeTypeSchema 类型
- `api/orchestrator.ts`（新增）：node-types API
- `utils/nodeSchema.ts`（新增）：schema 缓存 / 默认值补齐 / 必填校验 / 初始配置
- `utils/flow.ts`：NODE_TYPE_META 响应式化
- `views/app/agents/edit.vue`：挂载加载 schema、新建节点用 schema 初始配置
- `views/app/agents/components/NodeConfigPanel.vue`：默认值补齐 + 必填错误提示

## 五、后续迭代建议（历次未完成项，截至最新一轮）

1. **复杂控件 schema 化**：`branches`（条件）、`json`（headers）、`model/knowledge/tools/datasets` 下拉在 Schema 中已有类型定义，面板层仍使用专用组件承载，可逐步将选项数据源收敛到 schema 描述。
2. **结构化变量 v2 深层落地（DSL v2）**：本轮已落地「渲染层路径引用内核」（见六-4）；后续大项——节点输出真正对象化存储、循环/迭代与子流程节点、基于对象的流式分块渲染，仍为独立工程，建议单开工单推进。
3. **运行中断增强**：当前 `cancel` 为优雅取消（在途节点自然返回），如需「强制中断 + 客户端断线即取消模型调用」，需在节点执行层引入中断令牌与模型调用透传。

## 六、本轮（2026-09 实时运行监控批次）增补交付

### 1. 画布实时运行监控（SSE + 取消）
- 引擎：`WorkflowEngine.run(..., runListeners)` 按次订阅；`cancel(runId)` 优雅取消；`NodeStatus.CANCELED`（canceled 终态随 trace / 记录持久化）。
- Web：
  - `POST /api/app/agents/{id}/run-stream`（text/event-stream），帧协议 `{"type","data"}`：`run-started`→`node-started`→`node-finished`（TraceItem）→`done`（RunResult）；客户端断开自动取消本次运行。
  - `POST /api/agent-runs/{runId}/cancel` 取消运行中工作流。
- 前端 `edit.vue`：运行调试改为 SSE 实时执行——节点逐个点亮（run-running 呼吸态）、实时轨迹追加、结束时整链落色；新增「停止」按钮（取消后以 canceled 终态收敛界面）。

### 2. 保存前全流程 Schema 校验
- `edit.vue publish`：error 级（validateWorkflow 必填缺失）为硬阻断 + `ElMessageBox.alert` 定位首个问题节点；结构 / 效果类提示允许确认后继续。节点缺失角标（红 ⚠ 优先于黄 ⚠）沿用画布既有实现。

### 3. 运行记录监控页
- 新增 `frontend/src/api/agent-run.ts`、`views/ops/runs/index.vue`（运行记录页：状态过滤 / 应用 / 输入结果摘要 / 展开查看轨迹回溯与回答），注册路由 `/ops/runs` 与菜单「运行记录」；列表复用既有 `agent_run` 持久化与 `/api/agent-runs` 分页/详情接口，前端按 appId 映射应用名。

### 4. DSL v2：结构化变量渲染内核（渐进兼容）
- `VariableRenderer` 新增路径引用语法：`{{节点id.字段}}` / `{{n1.rows[0].name}}` / `{{input.weather}}`；
  基础值为合法 JSON 时沿路径取值（标量转文本、对象/数组压成紧凑 JSON），不可解析/缺失渲染为空；
  纯文本与含点旧变量名行为不变（完整键优先）。存储层仍为字符串，零 handler 改动即支持 code/http/知识库节点输出 JSON 后被模板化引用。
- `VariableRendererTest` 扩展至 14 例（新增 9 例路径引用），全部通过。
