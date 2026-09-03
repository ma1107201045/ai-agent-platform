# agent-workflow（agent-orchestrator）模块优化分析

> 说明：Maven 模块 `artifactId = agent-orchestrator`，其 `<name>` 为 `agent-workflow`，即本报告分析对象。
> 它承载平台最核心的「DAG 工作流执行引擎」。本文从 **分层 / 扩展 / 维护** 三个维度评估现状并提出可落地的改造方案。

---

## 0. 现状快照

| 项 | 现状 |
|---|---|
| 引擎核心 | `WorkflowEngine`（511 行单类）：DAG 拓扑调度、并行 fork/join、排他分支、节点级策略、环/可达性检测、轨迹归集、最终回答计算 |
| 节点扩展 | `NodeType` 枚举（9 种）+ `NodeHandler` SPI + `NodeHandlerRegistry`（Spring 自动收集，支持覆盖） |
| 内置节点 | start / end / llm / agent / condition / code(ＭVEL) / http / template / knowledge |
| 变量系统 | `VariableRenderer`：`{{input}} / {{节点id}} / {{变量名}}` 纯字符串替换，值存于 `Map<String,String>` |
| 外部能力 | 3 个 SPI（`ModelProvider` / `KnowledgeProvider` / `AgentRunner`），业务实现在 `agent-service` |
| 依赖 | `agent-common`（异常）、`agent-llm`（模型 SPI 与 DTO）、Spring、Jackson、MVEL |
| 主要调用方 | `ChatConversationService.send()`（同步 readValue → engine.run → 返回 answer+trace） |

**总体判断**：调度模型设计扎实（拓扑序 + fork/join + skip 语义 + 错误策略），是项目里质量最高的模块。
但存在三类结构性欠账：**① 引擎与 Spring/LLM 分层不够纯；② 字符串变量模型限制表达力，是扩展天花板；③ 无 schema 单源、无测试、单类过大，维护与新增节点成本逐节点上升。**

---

## 1. 分层角度

### 1.1 问题

**P1-1 引擎核心与 Spring 强耦合，无法脱离容器运行与测试**
- `WorkflowEngine`、`NodeHandlerRegistry`、全部 9 个 handler、`VariableRenderer` 都带 `@Component`；
- `WorkflowGraph` 解析、handler 收集都依赖 Spring；节点 handler 构造函数里 `@RequiredArgsConstructor` 注入业务 bean。
- 后果：DAG 调度这种**纯逻辑**没法做轻量单测（文档已指出全项目零测试）；引擎也无法作为纯库复用（如命令行、测试夹具、其他框架）。

**P1-2 「引擎」与「内置节点库」同 jar 混装，复用与语义未分层**
- 理论上 DAG 调度器是通用资产，9 个节点是"业务语义实现"，两者可被独立演进/替换，但目前同包耦合。
- handler 甚至直接 `import agent-llm` 的 `ChatMessage / ChatRequest / ChatResponse` 拼请求，即节点语义对模型 DTO 是**编译期硬依赖**，绕过了 `ModelProvider` 抽象的含义（该 SPI 仅返回 `ChatModel`，但 `LlmNodeHandler` 内部又自建了 `ChatRequest` 结构）。

**P1-3 `NodeContext` 是 9 元组 record，新增能力必然破坏所有既有实现**
```java
public record NodeContext(node, appId, userInput, outputs,
                          renderer, modelProvider, knowledgeProvider,
                          agentRunner, objectMapper) { ... }
```
- 再加一个能力（记忆、文件、定时器、事件总线），就要改 record 签名、改构造、改 `WorkflowEngine.newContext`、影响所有 handler —— **扩展一次动全身**。
- 且 `NodeContext` 直接暴露可变 `outputs` 全局表（`emit()` 直写共享 Map），让 handler 拥有"写全局状态"能力，隔离性弱、并发心智负担重。

### 1.2 改造建议

**S1-1 物理/逻辑拆分 engine-core 与装配层**（推荐逻辑分层，物理拆分可选）

```
agent-orchestrator
├── engine            (纯 Java：无 Spring 注解)
│   ├── model        WorkflowGraph / NodeType / DSL version
│   ├── runtime      DagScheduler / ExecutionScope / NodeInvoker
│   ├── validate     GraphValidator(环/可达性/连线合法性)
│   ├── variable     VariableValue + VariableRenderer
│   ├── spi          NodeHandler / NodeEventListener / CapabilityProvider
│   └── result       RunResult / TraceItem
└── nodes (或独立模块 agent-workflow-nodes)
    ├── spring       装配层：@Component 收集、Executor 生命周期、SPI 注入
    └── builtin      9 个内置 handler（依赖 agent-llm 抽象即可）
```
- 引擎核心零 Spring 依赖 → 可直接 JUnit 测试调度语义；
- 装配层只做"发现与接线"，替换 Spring 版本/框架不影响引擎。

**S1-2 用「能力门面」替代位置参数 record**
- 定义 `CapabilityRegistry`（或按需注入接口集合），`NodeExecutionContext` 仅持有 `context` 与能力查找器；
- handler 通过 `ctx.capability(ModelProvider.class)` 或 `ctx.tool()/ctx.memory()` 取能力；
- 新增能力 = 新增一个 SPI 实现并注册，**不触碰任何既有 handler 签名**。

**S1-3 澄清分层边界与命名**
- 统一表述：`agent-orchestrator`（name=agent-workflow）依赖方向应为
  `engine(spi) ← agent-llm(spi+dto) ← agent-service(业务实现)`；
- 模型 DTO 是否允许 handler 直接使用，需显式决策：若接受「agent-llm 即中立模型抽象层」，则 `ChatRequest` 等应被规范为**跨供应商中立消息格式**（当前可能带 OpenAI 倾向），并禁止 handler 越过 `ChatModel` 另起炉灶。

---

## 2. 扩展角度（本模块最值得投入的方向）

### 2.1 问题

**P2-1 变量系统只有字符串，结构化数据无法流转 —— 扩展天花板**
- `outputs: Map<String,String>`，全部输出都是文本接力：
  - Knowledge 节点「json 输出」实际是 `writeValueAsString` 的**字符串**；
  - Code 节点返回对象时被 `String.valueOf` 压平；
  - 下游无法做 `{{节点id.data[0].title}}` 路径引用，无法把对象/数组喂给 HTTP body、模板、分支判断。
- 而**分支判断、HTTP body、模板拼接、循环**这些高级编排都依赖结构化变量。不升级此模型，工作流表达力封顶。

**P2-2 节点"配置 schema"不存在，前后端靠手写双份同步**
- 每个节点的字段集合、默认值、必填、表单控件类型，只存在于三处且互相独立：
  1. handler 的 javadoc 与 `cfgXxx("key")` 散落调用；
  2. 前端 `flow.ts` 的 `defaultConfig()` 手写默认值；
  3. 前端 `NodeConfigPanel.vue` 等**手写每类节点的表单 UI**。
- 新增一个节点类型：后端加枚举 + handler（3 步）之外，**前端还要同步改 meta、默认值、表单面板**，任何一处遗漏就是线上 bug，且编译期无法发现。

**P2-3 DSL 无版本、无事件、无取消、无运行标识**
- `WorkflowGraph` 没有 `version` 字段，图结构演进无法做迁移（画布已可发布多版本 `AppAgentVersion`，两者未对齐）；
- 执行是"一次性同步 run"，中间过程不可见：**无法做节点级进度推送、运行记录持久化、取消/中断**；
- 对应前端「运行监控」「对话调试实时轨迹」能力缺失，`streamSend()` 对 workflow 模式直接抛异常（文档已列为短板）。

**P2-4 节点扩展的"声明"维度不足**
- `NodeType` 只有 `branch` 布尔，但出边 handle 集合、输入/输出变量声明、运行 UI 图标分组等都未建模；
- 未来 for 循环、迭代器、人工审批（wait）、子流程（subflow）等节点，现有 "branch bool + selectedHandle 约定" 模型需要再扩展。

### 2.2 改造建议（按价值排序）

**S2-1 升级为结构化变量模型（核心投资，建议排第一）**
- 引入树形变量值：`VariableValue = String | Number | Boolean | List | Map`；
- `outputs` 改为 `Map<String, VariableValue>`，节点可声明 `outputSchema`（`output: object/array/string`）；
- `VariableRenderer` 支持路径引用 `{{nodeId.a[0].b}}`（已有 `JSONPath` 基础可复用）；
- Code 节点 MVEL 返回复杂对象直接入库；HTTP body 模板可直接引用对象字段而非先 JSON.stringify。
- 影响面：`NodeContext`、`VariableRenderer`、code/llm/knowledge/template/http 若干 handler + 前端变量插入面板。建议**渐进式**：新 DSL `version=2` 支持结构值，旧图仍按字符串兼容渲染。

**S2-2 节点 Schema 单源化（新增节点成本从 5 步降到 2 步）**
- 每个 handler 提供声明式 schema（注解或独立描述类）：
  `@NodeField(key, label, type, required, default, options, scope=static/var/text-area…)`；
- 暴露 `GET /api/orchestrator/node-types` 下发 `[{code, label, branch, icon?, fields:[...]}]`；
- 前端 `NODE_TYPE_META / defaultConfig / NodeConfigPanel` 由 schema **自动生成**，后端 `validate()` 也由 schema 驱动（`NodeContext.cfgXxx` 大量手写解析收敛为 schema 绑定）。
- 结果：新增节点类型 = `NodeType` + `@NodeField handler` 两处，前后端零手写。

**S2-3 执行生命周期事件化（支撑可观测/流式/监控页）**
- 引入 `NodeEventListener` SPI：`onFlowStart(runId)/onNodeStart/onNodeEnd/onBranchTaken/onFlowEnd/onError`；
- 引擎 run 增加 `runId + startedAt` 元信息，事件可被：
  - 聊天页转为 SSE 进度流（workflow 流式化的第一步）；
  - 持久化为 `agent_run` 表，支撑「运行监控」列表与单次运行详情；
  - 计费/埋点（现在 usage 只统计到会话级）。
- 扩展点开闭：引擎只发事件，不感知消费者。

**S2-4 DSL 版本化 + 取消语义**
- `WorkflowGraph` 增加 `version`，内置 `graph-{from}-to-{to}` 迁移器注册表（启动校验 + 保存时自动迁移）；
- 提供 `run(runId, graph, input)` + `cancel(runId)`（向调度链传播中断），并行子任务 `CompletableFuture` 已具备取消基础。

---

## 3. 维护角度

### 3.1 问题

**P3-1 `WorkflowEngine` 单类承载 5 个职责，演进互相拖累**
511 行内混有：状态模型（`RunState`）、调度释放（`processNode/releaseEdges/skipNode`）、执行策略（重试/超时/onError）、校验（可达性+环检测）、回答计算（`computeAnswer` 的 5 级启发式）。任何一处改动的测试面覆盖全部路径，且 `computeAnswer` 的业务倾向被埋在引擎里。

**P3-2 线程池实例字段、无生命周期管理**
- `executor`（固定 4）/`elasticExecutor`（弹性）是引擎实例字段且无 `shutdown`，完全依赖 Spring 容器销毁；并行度、超时兜底均为硬编码常量，无法按部署调节。

**P3-3 配置读取为"魔法字符串 + 手写解析"，无类型安全**
- handler 里 `cfgStr("modelId")/cfgInt("topK")/cfgLongList("toolIds")` 散布；字段 key 无编译期约束，重构易漏；`NodeContext` 承担了过多的 JSON 类型判断逻辑。
- 分支 handle 约定值（`"true"/"false"/"else"`）、默认值、标签文本零散于前端 `flow.ts` 与后端 javadoc。

**P3-4 错误语义混在回答里**
- 节点失败时引擎把 `"节点执行失败：xxx"`（中英混杂、含内部 label）直接作为最终 answer 返回给用户；`trace.error` 与最终回复没有分开，产品层无法区分"技术错误"与"面向用户话术"。

**P3-5 运行无持久化标识，trace 一次性挥发**
- `RunResult` 仅 `answer + trace`，无 runId / 开始时间 / 每次请求的图指纹；调试与审计只能靠会话消息里存的 traceJson 快照。

**P3-6 零测试覆盖**
- 调度是纯逻辑、并发密度高（共享 `RunState` 线程安全），是最该有单测的模块：拓扑序正确性、join 等齐、condition 未选分支 skip、onError 连锁跳过、retries 退避、环检测、超时中断、`computeAnswer` 优先级。

### 3.2 改造建议

**S3-1 按职责拆类**（保持包结构，不做物理拆分也可先做类拆分）
```
WorkflowEngine            → 门面：仅编排调用
GraphValidator            → bfsReachable / detectCycle / 边合法性
DagScheduler (RunState)   → processNode / releaseEdges / skipNode 释放语义
NodeInvoker               → 重试/退避/超时/onError/fallback 执行策略
ExecutionTracker          → trace 记录、截断、run 元信息
AnswerAssembler           → computeAnswer 抽为策略接口，可插拔
```

**S3-2 线程池生命周期与配置外部化**
- 将 `ExecutorService` 抽为 `WorkflowExecutor`（`@Bean(destroyMethod="shutdown")`），并行度/队列/兜底超时走 `application.yml`；
- 每个 `run` 独占一个"调度令牌/线程组"而不是全部任务共享固定池，避免高并发相互饿死（文档亦已提示此风险）。

**S3-3 Schema 收敛配置读取**
- 由 S2-2 的 schema 生成每节点类型的强类型 Config（或统一校验器），`NodeContext.cfgXxx` 只留兼容垫片并逐步下线；常量（handle 约定、默认值）内聚到 `NodeType` / 各 handler 常量类，前后端 schema 同源。

**S3-4 回答与错误分离**
- `RunResult` 增加 `RunStatus(status/errorCode/errorDetail)`，`answer` 永远给用户可读内容（模板/兜底），技术细节放 `trace.error` + `errorDetail`；
- 节点失败时走「回答模板 / end 节点 fallback」而非把异常文本拼给用户。

**S3-5 补齐关键单测矩阵（建议配合 S3-1 立即做）**
```
- 线性/菱形/并行 fork → join 等齐
- condition 选中分支执行、未选分支整链 skipped
- onError: fail 连锁跳过 / continue / fallback
- retries 与 ConfigException 不重试
- 环检测 / 不可达节点（自环、跨层环）
- 超时中断与弹性池隔离
- computeAnswer 5 级优先级
- VariableRenderer 边界（{{}}缺失、特殊字符、长文本）
```

---

## 4. 目标分层结构（改造后）

```
┌──────────────────────────── agent-service ────────────────────────────┐
│ ModelProviderImpl / KnowledgeProviderImpl / AgentRunnerImpl (业务适配)  │
│ ChatConversationService / AppAgentService (调用方/持久化 run 记录)        │
└──────────────▲─────────────────────────────────────────────────────────┘
               │ 仅依赖 SPI
┌──────────────┴─────────────────────────────────────────────────────────┐
│                       agent-orchestrator (name=agent-workflow)         │
│  ┌───────────────────────── engine-core（纯 Java，可单测）────────────┐ │
│  │ model / validate / runtime(scheduler) / variable / result / spi    │ │
│  │   NodeHandler │ NodeEventListener │ CapabilityProvider             │ │
│  └──────────────────────────────┬─────────────────────────────────────┘ │
│  ┌──────────────────────────────▼─────────────────────────────────────┐ │
│  │ 装配层(spring)：Registry 收集、Executor 生命周期、事件分发            │ │
│  │ 内置节点库(nodes)：llm/agent/knowledge/http/code/... 仅依赖 agent-llm│ │
│  └────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
               ▲                ▲                    ▲
     agent-llm(spi/dto)   frontend(schema 渲染)   DSL v2(version+迁移)
```

---

## 5. 改造路线图

### P0 治理类（低成本、立即受益、风险最低）
1. **补齐调度单测**并抽出可测类（先于重构，防止回归）；
2. `WorkflowEngine` 拆 `GraphValidator / NodeInvoker / AnswerAssembler`，行为不变；
3. 线程池生命周期 `@Bean(destroyMethod)` + 配置外部化；
4. `RunResult` 增加 runId/时间/状态字段，**回答与错误分离**；
5. 模块命名澄清（artifactId 与 `<name>` 二选一并统一注释里的叫法）。

### P1 扩展类（支撑 roadmap，影响面大但收益最大）
6. **节点 schema 单源化** + `/node-types` 下发 + 前端自动渲染；
7. **结构化变量模型**（DSL v2，渐进兼容 v1）；
8. `NodeEventListener` + run 记录持久化 → workflow 聊天 SSE 进度、运行监控页、取消/中断；
9. DSL `version` + 迁移器。

### P2 能力类（产品差异化）
10. 循环/迭代节点、子流程 subflow、人工审批 wait 节点；
11. MVEL 代码节点沙箱/白名单加固；
12. 流式 LLM 节点（边生成边推送）与多 end 结果聚合。

---

## 附：与现有短板文档的呼应

- 文档第 9 条「workflow / agent 模式均同步阻塞、SSE 仅直连」 → 对应 **S2-3 / P1-8**；
- 文档第 5 条「向量检索 O(n)」不在此模块，但 Knowledge 节点的 `KnowledgeProvider` SPI 隔离良好，未来换向量库只需改实现；
- 文档第 10 条「线程池无 shutdown」 → 对应 **P3-2 / P0-3**；
- 文档第 6 条「零测试覆盖」 → 对应 **P0-1**。

> 建议按 P0 → P1 顺序推进；其中 **S2-2 节点 schema** 与 **S2-1 结构化变量** 是后续一切高级编排能力（循环/子流程/流式/运行监控）的地基，优先于 P2 功能堆叠。
