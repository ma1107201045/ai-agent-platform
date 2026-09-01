# AI 智能体平台 · 项目分析报告

> 分析时间：2026-09-01 · 基于工作区当前（含未提交改动）状态

---

## 一、项目定位

一个**自研的 LLM 智能体应用平台（Dify / Coze 类）**，提供「模型接入 → 知识库 → 工具 → 可视化工作流编排 → 应用发布 → 对外对话」的完整闭环。

- 后端：Java 21 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + MySQL 8
- 前端：Vue 3.4 + TypeScript 5.4 + Vite 5 + Element Plus + @vue-flow（画布编排）
- 构建：Maven 多模块（6 个）
- 规模：已跟踪源码 **132 个文件 / 约 14,000 行**（Java 97、Vue 16、TS 19、SQL 1）

---

## 二、后端模块结构

| 模块 | 职责 | 关键类 |
|---|---|---|
| `agent-common` | 统一响应、异常、JWT、用户上下文 | `Result` / `GlobalExceptionHandler` / `JwtUtil` / `UserContext` |
| `agent-llm` | 模型 SPI 抽象 + OpenAI 兼容实现 | `ChatModel`/`EmbeddingModel`/`RerankModel`，`LLMFactory`，`OpenAIChatModel` |
| `agent-dao` | 实体 + Mapper（12 张表） | `AppAgent` / `KnowledgeChunk` / `ModelInfo` / `SysUser` 等 |
| `agent-workflow` | **DAG 工作流引擎（核心资产）** | `WorkflowEngine` + 9 种 `NodeHandler` |
| `agent-service` | 业务服务 | `AppAgentService`（含 ReAct 循环）/ `KnowledgeService` / `ModelService` / `ChatConversationService` |
| `agent-web` | 启动器 + Controller | `AuthInterceptor`、8 个 Controller |

### 工作流引擎（`agent-workflow`，成熟度最高）

- **DAG 并行调度**：入度拓扑序 + fork/join，固定池（4 线程）+ 弹性池（带超时的节点）
- **9 种节点**：start / end / llm / agent / condition（排他分支）/ code / http / template / knowledge
- **执行前校验**：可达性 BFS + Kahn 环检测，环形/不可达结构直接报错
- **节点级执行策略**：`retries`（退避重试）、`timeoutSeconds`、`onError`（fail/continue/fallback）、`outputVar`（输出变量别名）
- **变量系统**：`{{input}}`、`{{节点id}}`、`{{别名}}` 渲染
- **可观测轨迹**：每节点记录 状态/输入/输出/耗时/错误，截断 300 字符
- **扩展方式**：实现 `NodeHandler` 并交给 Spring 即可，**新增节点类型无需改动引擎**

### LLM 抽象层（`agent-llm`）

- SPI 三接口：`ChatModel`（call + stream）/ `EmbeddingModel` / `RerankModel`
- OpenAI 兼容实现，支持 **Function Calling + SSE 流式**
- 已适配 DeepSeek（种子数据默认供应商）

### Agent 自主执行（ReAct）

`AppAgentService.doChat()`：最多 6 轮「规划 → 工具调用 → 观察」循环；工具支持 **HTTP**（GET 拼 query / 其余 JSON body，Bearer 鉴权）与 **MVEL 代码脚本**；自动注入应用绑定知识库上下文。

### 知识库（RAG）

- 文档解析：txt / md / **pdf**（PDFBox）/ **docx**（POI，含表格）
- 滑动窗口分块（默认 500 字符 / 重叠 50，优先在句号换行处切分）
- 批量向量化（批大小 16），**向量以 JSON 存 MySQL longtext，检索时全量加载做内存余弦相似度**
- 可选 Rerank 二次精排，rerank 失败自动降级

### 数据模型（12 张表）

`app_agent`(应用) · `app_agent_version`(版本快照) · `app_agent_tool`(工具) · `chat_conversation` · `chat_message`(含 `trace_json` 轨迹) · `knowledge_dataset` / `knowledge_document` / `knowledge_chunk` · `model_provider` / `model_info` · `sys_tenant` / `sys_user`

---

## 三、前端功能现状

**菜单 10 个分组、40 个入口，其中已上线 11 个，其余 29 个为「建设中」占位页**（路由里已规划好 desc / features / dependency）。

已上线：工作台概览、智能体（列表/画布编排/对话调试）、知识库、工具管理、发布管理、运行监控、对话记录、供应商管理、团队与权限、登录、公开对话页 `/public/:id`。

规划中的模块已标注优先级：
- **P0（7 个）**：提示词库、记忆管理、渠道管理、API 密钥、用量统计、评测中心、账号与安全
- **P1**：应用市场、多智能体编排、内容安全、版本历史、模型网关、评测数据集、对比实验等
- **P2**：通知中心、定时任务、插件市场、模型微调、操作日志、回收站等

---

## 四、当前进行中的工作（未提交）

一次**全局命名规范化重构：`App` → `AppAgent`**，遵循「表名 → 实体 → Mapper → Service → Controller → URL」对齐规则：

- 后端：`AppService` → `AppAgentService`（删除旧类）、`AppToolService` → `AppAgentToolService`、`AppController` → `AppAgentController`、`AppToolController` → `AppAgentToolController`，URL 统一为 `/api/app-agents`
- 前端：`api/app.ts` → `api/app-agent.ts`、`api/tool.ts` → `api/app-agent-tool.ts`；`views/apps/` → `views/app-agents/`（新增，旧目录待删除）
- 同步更新：`router/index.ts`、`config/menu.ts`、`utils/flow.ts`、`api/public.ts`、`api/types.ts`
- 新增未跟踪目录：`docs/`、`frontend/src/views/app-agents/`

**注意**：旧目录 `frontend/src/views/apps/` 目前是「已删除未暂存」状态，而新目录 `frontend/src/views/app-agents/` 是「未跟踪」状态 —— 重构尚未 `git add`，此状态下若误操作容易丢失代码。

---

## 五、主要问题与风险

### 高优先级

1. **密钥与密码硬编码并已提交进 Git**
   `agent-web/src/main/resources/application.yml` 已被 Git 跟踪，内含 MySQL 明文密码、JWT 密钥（`agent-platform-jwt-secret-change-me-...`）。应改为环境变量/配置中心，并把文件移出版本控制。

2. **`sql/init.sql` 与注释声明矛盾，重复执行会清库**
   文件头声称「幂等、可重复执行、自动补齐缺失列（见文末增量补丁）」，但实际全文是 `DROP TABLE IF EXISTS` + `CREATE` + `INSERT` 种子数据，**且文末并无增量补丁**。对已有数据的库执行该脚本将**清空全部业务数据**（应用、会话、知识库、模型配置）。

3. **MVEL 脚本执行无沙箱**
   代码工具与 Code 节点通过 `MVEL.executeExpression` 执行用户脚本，脚本内可直接 `new java.util.Date()` 等任意 Java 调用（种子数据即如此）。多租户/对外场景下等同于任意代码执行。

4. **多租户形同虚设**
   `tenant_id` 字段普遍存在，但查询几乎**未做租户过滤**（如 `AppAgentService.page`、`KnowledgeService.datasetPage` 均直接全表分页）；`create` 时兜底硬编码 `tenantId = 1L`。存在跨租户数据泄露风险。

### 中优先级

5. **知识库检索为全表内存计算**
   `search()` 每次把数据集**全部分块**查出后逐条算余弦相似度，O(n) 且随数据量线性劣化；向量以 JSON 文本存储，无索引。数据量上千后将成为瓶颈，需引入向量库（pgvector / Milvus）或至少加缓存 + 预筛选。

6. **零测试覆盖**：全项目无任何单元测试 / 集成测试文件。工作流引擎这类高复杂度调度逻辑（环检测、分支释放、join 语义）尤其需要测试保护。

7. **无 README / 无部署文档**：新成员无法自助上手；`docs/` 目录仅有一个分析报告 HTML 且未纳入 Git。

8. **对外接口无防护**：`/api/portal/public/**` 免鉴权，无频率限制、无配额、无内容安全护栏，直接暴露 LLM 调用，易被刷量产生费用。

9. **流式能力不完整**：SSE 流式仅支持「直连模型」模式，workflow / agent 模式均为同步阻塞返回。

### 低优先级 / 工程细节

10. `WorkflowEngine` 中 `executor` / `elasticExecutor` 为实例级线程池且无 `shutdown`，依赖 Spring 容器生命周期；`PortalPublicController` 每次请求都重新解析 DSL JSON，可考虑缓存。
11. 删除操作均为**物理删除**（应用、会话、消息、知识库、模型），与规划中的「回收站」模块冲突，需提前改为软删除。
12. 前端 `planned` 页面占比 72%（29/40），产品完成度与菜单广度不匹配，建议菜单按 `phase` 分级展示，避免「空壳感」。

---

## 六、总体评价

**这是一个完成度不错的技术骨架，核心引擎质量明显高于业务完成度。**

- **优势**：分层清晰（SPI 抽象解耦模型/工具/知识库）、工作流引擎设计扎实（DAG 并行 + 分支语义 + 错误处理策略 + 轨迹可观测 + 开闭扩展）、命名规范正在被系统性治理、前端对未完成模块做了诚实的「建设中」占位而非留空。
- **短板**：安全（密钥硬编码、MVEL 沙箱、租户隔离）、性能（向量检索）、工程质量（零测试、无文档）三块欠账较多；`init.sql` 的清库风险需立即处理。

**建议下一步顺序**：① 修复 `init.sql` 破坏性问题 → ② 密钥外置 + 租户过滤 → ③ 补齐工作流引擎单测 → ④ 补充 README 与部署文档 → ⑤ 沉淀 P0 模块（提示词库、API 密钥、用量统计）。
