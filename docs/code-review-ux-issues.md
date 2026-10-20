# 前后端逻辑审查报告：不合理设计与用户体验问题

> 审查范围：后端 6 个模块（agent-common/llm/dao/orchestrator/service/web）+ 前端 frontend/（Vue3 + Element Plus）
> 审查角度：从"普通用户简单操作"出发，找出阻断性 bug、繁琐交互、不安全/不一致的设计

---

## 一、阻断性问题（用户一用就撞上，建议立即修复）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| F1 | **多处跳转指向不存在的路由 `/app-agents`** | `frontend/src/views/chat/index.vue:71`、`app/agents/chat.vue:310`、`dashboard/index.vue:52,101`（路由表注册的是 `/app/agents`） | 工作台首页"创建应用"按钮、对话记录页"继续对话"、调试页"返回"**全部 404**，核心入口直接坏掉 |
| F2 | 对话记录页"模式筛选"下拉是假筛选，选中后请求根本不传该参数 | `chat/index.vue:28,46-59,175-183` | 欺骗性 UI，筛选无效 |
| F3 | 数据存储页"搜索数据表"输入框无任何事件绑定，输入不触发查询 | `storage/index.vue:25-29` | 搜索框是摆设 |
| B1 | **真实 API Key 被提交进 Git**（DashScope `sk-34cd...`、火山方舟 `ark-0ff6...`、admin bcrypt 哈希） | `sql/agent_platform_pg.sql:1366,1367,1707` | 密钥泄露，应立即吊销并轮换 |
| B2 | **事务内同步调用 LLM/工作流**，流式生成全程持有 DB 连接 | `ChatConversationServiceImpl.java:206-250,255-284` | 几十个并发聊天就耗尽连接池，全站（含登录）超时；流式中途失败还会回滚已保存的用户消息 |
| B3 | 只有登录校验、**无任何角色/权限体系**：删用户、清空回收站、改供应商 key、任意调用付费模型全员可用 | `AuthInterceptor.java:29-47`；`SysUserController`/`TrashController`/`ModelController` 均无鉴权 | 普通用户可删管理员、烧光 API 额度 |

---

## 二、前端：繁琐/反直觉的交互

1. **"团队与权限"页名不符实**（`users/index.vue:24-36`）：页面只有用户列表，创建用户只有一个输入用户名的 prompt——**没有密码、邮箱、角色字段**，建出来的用户不知道怎么登录；无搜索、无编辑、无启停。
2. **筛选状态路由往返后全部丢失**（所有列表页，如 `app/agents/index.vue:16-19`）：搜索到第 3 页 → 点进编辑 → 返回，搜索词和页码全没了。建议筛选条件同步到 URL query。
3. **工具管理列表无搜索/筛选**（`tools/info/index.vue:288-297`），与技能页、提示词页的完整筛选不一致，工具多了只能翻页找。
4. **数据行只能逐条删除，无批量操作**（`storage/index.vue:128-167`）：清理几十行测试数据要点几十次"删除→确认"。
5. **轨迹折叠面板状态被所有消息共享**（`MessageList.vue:30,95-141` + `chat.vue:265`）：新消息返回会把**所有历史消息的轨迹面板全部撑开**，瞬间刷屏。
6. **workflow/agent 模式下"停止生成"按钮是摆设**（`chat.vue:241,287-289`）：非流式分支没接 AbortController，点了毫无反应。
7. **流式输出期间强制滚底**（`chat.vue:246-248`）：生成中用户无法上翻回看。讽刺的是 `ModelTestDialog.vue:28-51` 已实现"上翻暂停跟随"，对话页却没复用。
8. **无"重新生成"按钮、整条消息无复制按钮、超长回答无折叠**（`MessageList.vue:77-144`）：不满意只能重发一条。
9. **编辑器发布后 dirty 标记不清除**（`edit.vue:660-670`）：发布后标题栏仍显示"未保存"，离开时还弹"有未保存修改"，用户无法判断发布是否生效。
10. **评测中心每 2.5 秒整表闪 loading 遮罩**（`eval/index.vue:82-93,312`）：轮询期间根本没法看表格；且固定间隔无退避、后台标签页也在刷。
11. **素材上传 UI 写着"不超过 20MB"但零校验**（`assets/index.vue:144,356-376`）：超限要传完才被拒；20 个文件串行上传无进度条，第 15 个失败时前 14 个已传，重试从头再来产生重复。知识库上传（`knowledge/index.vue:193-196`）同样问题。
12. **对外公开对话页不支持流式**（`public/chat.vue:51-83`）：慢模型下用户对着转圈气泡等几十秒；且全量历史无截断无限增长，越聊越慢越容易超 token。
13. **登录页预填 admin/admin**（`login/index.vue:14-17`）：安全隐患 + 误导。
14. **取消删除产生未捕获 Promise rejection**（`storage/index.vue:296-334`、`chat/index.vue:99-108`）：同项目内 `chat.vue:177-193` 写法正确，标准不一。
15. **危险操作确认弹窗类型/文案不统一**：删应用是 error 红框含"不可恢复"，删数据行只是 warning，删用户无后果说明；成功提示有"已删除/删除成功/记录已删除"三种。建议封装统一 `confirmDelete()`。
16. **启停开关有的有失败回滚有的没有，全部无防连点**（对比 `tools/info/index.vue:126-136` ✅ vs `skills/index.vue:98-103`、`prompts/index.vue:60-65` ❌）；prompts 还用 `{...row}` 全量回传，把只读字段也提交了。
17. **画布历史栈每次操作全量深拷贝 + 深度响应式化**（`useFlowHistory.ts:28-39` + `edit.vue:360-374` 两个 deep watch）：大画布拖动、打字明显卡顿。历史栈应改 `shallowRef`，dirty 检查改显式 `markDirty()`。

---

## 三、后端：让用户操作变复杂的 API 设计

1. **REST 命名混乱**：`/api/app/agents`（三段）vs `/api/agent-teams` vs `/api/sys/users` vs `/api/system/trash`（sys/system 混用，`TrashController.java:22`）vs `/api/data-store`；模型调用挂 `/api/model/models/chat` 而 CRUD 在 `/api/model/providers`。前端无法按规则推导路径。
2. **几乎全部接口无参数校验**：全项目仅 2 处 `@Valid`；`AppAgentController.create(@RequestBody AppAgent app)` 直接收实体（`tenantId` 等敏感字段可被冒充），`maxIterations=100000` 能直达 LLM。
3. **创建 Agent 暴露实体全部字段**（`AppAgentController.java:74`）：前端表单要处理一堆本可服务端默认的字段。应定义 `AppAgentCreateDTO{name, description?}`。
4. **默认模型取"第一个启用的 LLM"**（`ModelServiceImpl.java:165-169`）：管理员加个测试模型，所有公开应用回复风格突变，用户无感知。
5. **Agent 循环轮数无上限且同步阻塞**（`AppAgentServiceImpl.java:358,386-428`）：前端传 1000 就真循环 1000 次付费调用，请求挂起数分钟直到 504。应封顶 20 轮 + 改 SSE/异步。
6. **发布版本号 `max+1` 无并发保护**（`AppAgentServiceImpl.java:159-182`）：两人同时点发布会产生重复版本号。
7. **发布接口硬编码操作人 ID=1**（`AppAgentController.java:94`）：`UserContext` 就在手边却写死，审计失真。
8. **会话消息列表无分页**，且 `send()` 把全量历史无截断塞给 LLM（`ChatConversationServiceImpl.java:318-322`）：超上下文窗口后用户收到裸上游报错。
9. **知识库文档向量化在请求线程内同步完成且包着事务**（`KnowledgeServiceImpl.java:140-158`）：传 20MB PDF 要在上传按钮上等几分钟，用户以为失败重试产生重复文档。已有 `indexing/failed/ready` 状态字段，天然支持异步化。
10. **两套协议相同的流式接口并存**（`ModelInvokeController.java:55-107`）：`/chat-stream` 与 `/chat-stream/flux` 让前端二选一，属技术实验暴露成公开 API。
11. **错误提示不给出下一步动作**：500 错误直接透传上游 JSON（`GlobalExceptionHandler.java:52` + `OpenAiHttpClient.java:51`），用户看到 `"系统内部错误: LLM 调用失败 HTTP 400: {...}"`；"已达到最大迭代次数"不告诉用户去哪调。建议错误响应增加 `actionHint` 字段。
12. **删除/彻底删除无"影响预览"**：purge 一个 agent 级联删全部会话/消息/用量，用户事前不知道会丢什么。建议提供 `GET /trash/{type}/{id}/impact` 返回关联计数。
13. **无任何批量操作接口**：清理 100 个测试会话要点 100 次确认。

---

## 四、后端：安全与数据一致性（简要）

- **B4** 模型供应商 apiKey **明文存库且随接口明文返回前端**（`ModelProvider.java:31-32`、`ModelController.java:29-32`）——任何人打开模型管理页 F12 即拿到所有付费 key。
- **B5** 用户密码哈希随用户列表/详情泄露（`SysUser.java:26` 无 `@JsonIgnore`）。
- **B6** 回收站恢复/彻底删除无归属校验，可跨用户操作（`TrashServiceImpl.java:54-117`）。
- **B7** HTTP 节点无 SSRF 防护（`HttpNodeHandler.java:89-101`），公开访客可让服务器访问内网/云元数据。
- **B8** 素材上传可传 SVG/HTML 且 inline 渲染 → 存储型 XSS（`AssetServiceImpl.java:176-191` + `AssetController.java:65`）；素材接口也无归属校验。
- **B9** 登录无防爆破（`SysAuthServiceImpl.java:37-50`）：无失败锁定、无验证码、无限流。
- **B10** 门户限流信任 `X-Forwarded-For`，可伪造绕过（`PortalPublicGuard.java:42-47`）。
- **B11** 会话删除存在 status + deleted 双标记，语义冗余（`ChatConversation.java:38-50`）；全项目删除语义不统一。
- **B12** 数据表 rowCount 读-改-写无锁，createRecord 无事务（`DataStoreServiceImpl.java:170-209`），并发下计数永久漂移。
- **B13** 用户改名不查唯一性（`SysUserServiceImpl.java:65-74`），可造出重复用户名导致两人都无法登录。
- **B14** LLM HTTP 客户端 120s 整体超时会误杀长流式生成，且无重试（`OpenAiHttpClient.java:20-31`）。
- **B15** 多处 N+1 查询（`ModelServiceImpl.java:127-245` 循环查 provider；`AppAgentController.java:124-134` 批量接口循环单查）；`parseIds` 对非法 id 静默忽略。
- **B16** 无任何 CORS 配置（全项目 grep 零命中）、application.yml 仅 46 行无 profile 区分。

---

## 五、修复优先级建议

**P0（事故级，立即处理）**
- B1 吊销泄露的 API Key 并清理 git 历史
- F1 修复 `/app-agents` → `/app/agents` 路由跳转（核心入口 404）
- B2 LLM 调用移出事务
- B3/B4/B5/B6 权限体系 + 密钥/密码/回收站越权

**P1（用户每次都撞上）**
- F2/F3 假筛选、假搜索
- 前端 #5 轨迹面板刷屏、#6 停止按钮无效、#7 强制滚底、#10 轮询闪烁
- 后端 #5 Agent 轮数封顶、#8 历史截断、#9 向量化异步化、#11 错误提示带 actionHint

**P2（体验优化批次）**
- 前端 #7 用户管理补全、#15 确认弹窗统一、#2 筛选状态入 URL、#17 画布性能
- 后端 #1 REST 命名统一、#2 参数校验、#3 创建 DTO 收窄、#4 默认模型显式配置、批量操作接口

**值得保持的好做法**
- 前端 `api/request.ts` 统一错误提示 + 请求去重缓存；编辑器 dirty 追踪 + 路由守卫；技能删除前列出引用应用（值得推广）
- 后端引擎层 `WorkflowEngine`/`NodeExecutionPolicy` 的超时、重试、退避、取消语义设计扎实

---

## 六、修复进度（2026-10-19）

已修复并验证（`mvn clean compile` + `vue-tsc -b` 均通过）：

| 编号 | 修复内容 |
|------|---------|
| F1 | `/app-agents` → `/app/agents` 全部跳转修正（dashboard/chat/agents chat + 注释） |
| F2 | 会话列表后端接口补 `mode` 参数（Controller→Service→Impl→前端 api），模式筛选生效 |
| F3 | 数据存储搜索框补 `@keyup.enter`/`@clear` 触发 |
| B1 | SQL 种子数据 API Key 替换为占位符并加泄露警示注释（**真实 key 仍需到厂商后台吊销轮换**） |
| B2 | `send`/`streamSend` 去除方法级 @Transactional，改用 TransactionTemplate 短事务只包消息落库；用户消息先落库，LLM 失败不再丢记录 |
| B4 | `ModelProvider.apiKey` 分页/详情/创建响应统一脱敏（前 3 + **** + 后 4）；update 时掩码/空值不覆盖已存密钥 |
| B5 | `SysUser.password` 加 `@JsonProperty(WRITE_ONLY)`，不再随接口返回 |
| B6 | TrashMapper 列表查询加 `tenant_id` 过滤；restore/purge 前校验当前租户归属（定时清理无上下文时跳过） |
| 后端#5 | Agent 迭代轮数硬上限 20（HARD_MAX_ITERATIONS） |
| 后端#6 | `app_agent_version` 加 `(app_id, version)` 唯一索引 + DuplicateKeyException 友好提示 |
| 后端#7 | publish 操作人改用 `UserContext.getUserId()` |
| 后端#8 | LLM 历史截断为最近 20 条（MAX_HISTORY_MESSAGES），防超上下文窗口 |
| 前端#5 | 轨迹面板改为每条消息独立状态（`ChatItem.traceOpen`），不再一条展开全部撑开；public/chat.vue 同步修复 |
| 前端#6 | 所有模式统一创建 AbortController，`conversationApi.send` 接入 signal，"停止生成"全模式可用；兼容 axios ERR_CANCELED |
| 前端#7 | MessageList 实现 autoFollow（上翻暂停跟随、回到底部恢复，阈值 48px）；发送/打开会话强制滚底 |
| 前端#9 | 发布成功后同步保存草稿并清 dirty，提示"发布成功，当前画布已同步保存为草稿" |
| 前端#10/#12 | eval 轮询改静默刷新（不再闪 loading）+ 页面隐藏暂停/恢复即刷；顶栏未读数轮询同样处理 |
| 前端#16 | 公开对话页历史上限 20 条（slice(-20)） |
| 前端#14 | chat/index.vue、storage/index.vue 的删除确认补 try/catch，取消不再产生未捕获 rejection |

~~**未修复（需另行立项）**：权限/角色体系（B3）、apiKey 落库加密、SSRF 防护（B7）、SVG XSS（B8）、登录防爆破（B9）、REST 命名统一、批量操作接口、用户管理表单补全、画布历史栈性能（#17）等 P2 项。~~

---

## 七、修复进度（2026-10-19 第二批，P2）

已修复并验证（`mvn clean compile` + `vue-tsc -b` + `vitest useFlowHistory` 5/5 均通过）：

| 编号 | 修复内容 |
|------|---------|
| B3 | 轻量角色体系：`sys_user` 加 `role` 列（PG/MySQL 种子+迁移脚本）；JWT 携带 role 声明；新增 `@RequireAdmin` 注解由 AuthInterceptor 强制校验（403）；用户管理/回收站 purge+cleanup/供应商与模型写接口全部纳入管理员管控；前端按角色过滤菜单（供应商管理、团队与权限、操作日志仅管理员可见） |
| B4 深化 | apiKey **落库加密**：新增 `CryptoUtil`（AES-GCM，`enc:v1:` 前缀，`PLATFORM_CRYPTO_SECRET` 环境变量注入，未配置时明文兼容+警告）；创建/更新供应商自动加密，运行时解密，接口响应仍脱敏 |
| B7 | SSRF 防护：新增 `SsrfGuard`（拦截非 http/https、localhost、回环、内网 10/172.16/192.168、链路本地 169.254 云元数据、保留段）；HTTP 节点、工具调用、连接器连通性测试三处出口全部接入；HttpClient 禁止跟随重定向防 302 绕过 |
| B8 | 素材上传黑名单（svg/html/htm/js/xml 等可执行脚本类型，前后端双重拦截）；内容下载改为白名单 inline（仅 png/jpg/gif/webp 等），其余强制 attachment + `X-Content-Type-Options: nosniff` |
| B9 | 登录防爆破：按用户名内存计数，连续失败 5 次锁定 10 分钟，提示剩余锁定时间 |
| B13 | 用户改名查重（排除自身），避免重复用户名 |
| 前端#1 | 用户管理页重写：创建/编辑对话框含用户名、密码（编辑留空不改）、昵称、邮箱、角色、状态；列表加搜索、角色标签、启停开关（防连点+失败不回滚） |
| 前端#13 | 登录页移除 admin/admin 预填 |
| 前端#11 | 素材上传前端预校验（>20MB / 危险类型直接跳过并提示）；部分失败保留失败文件可重试，不再从头重传 |
| 前端#17 | 画布历史栈 `ref`→`shallowRef`（50 份整树快照不再深度响应式化）；dirty 追踪移除 nodes/edges 的 deep watch，改由结构变更统一入口 `snapshot()` 显式标脏 |
| 后端#13/前端#4 | 批量删除：会话 `POST /chat/conversations/batch-delete`（≤200，逐个校验归属）；数据行 `POST /data-store/records/batch-delete`（≤500）；前端对话记录/数据存储表格加多选+批量删除按钮 |
| B12 | 顺带修复：data_table rowCount 读-改-写改为原子 SQL 自增/自减（`COALESCE`+`GREATEST`），并发下计数不再漂移 |

**仍需用户手动处理**：吊销已泄露的 DashScope/火山方舟 API Key；生产环境配置 `PLATFORM_CRYPTO_SECRET`（配置后历史明文 key 仍可读，新写入自动加密）；老库执行对应迁移脚本（PG/MySQL 均已追加 sys_user.role 列）。

**剩余未做（建议后续立项）**：~~PortalPublicGuard 的 X-Forwarded-For 信任策略（B10）、LLM 客户端超时/重试调优（B14）、N+1 查询优化（B15）、CORS/环境 profile（B16）~~（均已在第三批修复）；REST 命名统一（sys/system 混用等，涉及前后端路径联动，属破坏性变更）、错误响应 actionHint 字段、回收站影响预览接口。

---

## 八、修复进度（2026-10-20 第三批，P2 收尾）

已修复并验证（`mvn clean compile` 通过，本批无前端改动）：

| 编号 | 修复内容 |
|------|---------|
| B10 | IP 识别统一收口：`PortalPublicGuard.resolveClientIp` 默认只信 TCP 直连地址，忽略可伪造的 X-Forwarded-For/X-Real-IP；仅 `portal.public.trust-proxy=true`（确认处于可信反向代理之后）才读取代理头。登录日志（SysAuthController）与操作日志切面（OperationLogAspect）改为共用同一策略 |
| B14 | LLM HTTP 客户端：连接超时从 120s 降为固定 10s；流式请求**移除整体超时**（长流式生成不再被 120s 误杀，依靠连接超时+调用方取消兜底）；非流式请求保留整体超时且网络 IO 异常自动重试一次（HTTP 错误状态不重试） |
| B15 | N+1 消除：`chatModels/allModels/embeddingModels/rerankModels` 四个方法合并为 `listEnabledModels`，供应商改批量 IN 查询；`playgroundModels` 同样批量化；新增 `AppAgentService.getPublishedVersions`（两次 IN 查询代替 2N 次），`/batch/published` 接口接入；`parseIds` 非法 id 由静默忽略改为明确报错 |
| B16 | CORS 显式配置：`/api/**` 仅放行 `cors.allowed-origins`（环境变量 `CORS_ALLOWED_ORIGINS`，默认仅 vite 本地端口），限定方法与请求头；`application.yml` 引入 profile（默认 dev），新增 `application-prod.yml`——生产环境 DB 密码/JWT/加密密钥/CORS 全部强制环境变量注入、关闭 Swagger；顺带移除 yml 中硬编码的本地数据库密码默认值 |

**至此报告列出的全部安全/性能项均已处理**；~~仅剩 REST 命名统一、错误响应 actionHint、回收站影响预览三个体验/规范性立项项~~（均已在第四批修复）。

---

## 九、修复进度（2026-10-20 第四批，体验/规范性收尾）

已修复并验证（`mvn clean compile` + `vue-tsc -b` 均通过）：

| 编号 | 修复内容 |
|------|---------|
| 后端#11 | **错误响应 actionHint**：`BizException`/`Result` 新增 `actionHint` 字段（null 不序列化）；全局异常处理器将 LLM 上游错误按状态码映射为友好提示+操作引导（401→检查 API Key、429→查额度、400→上下文超限、404→模型下线），不再透传原始 JSON；"请选择对话模型/未编排工作流/未发布"等高频错误均带下一步指引；前端 `request.ts` 错误 toast 换行展示提示并延长显示时长 |
| 后端#12 | **回收站影响预览**：新增 `GET /sys/trash/impact?type=&id=`，返回将被级联删除的子数据计数（版本/会话/消息/用量/文档/分片/成员等 13 个计数查询）；前端彻底删除确认框逐行列出"将同时删除：会话 N 条、消息 M 条……"，预览失败时退回通用提示不阻断 |
| REST 命名 | `/api/system/trash` → `/api/sys/trash` 与其他系统接口对齐；旧路径保留为兼容别名（`@RequestMapping` 双路径），前端已切至规范路径 |
| 后端#3 | **创建应用 DTO 收窄**：新增 `AppAgentSaveDTO`（仅 10 个可编辑字段 + 名称/长度校验），`tenantId/status/publishedVersionId/deleted` 不再接受客户端传入；`create` 租户归属强制取登录上下文 |
| 顺带修复 | `purgeAgentMessages` 的 `DELETE ... USING` 为 PG 方言，MySQL 下会语法错误——改为 `IN (SELECT ...)` 双库通用写法 |

**至此报告全部问题项均已处理完毕。** 仍需用户手动处理：吊销泄露 API Key、生产配置 `PLATFORM_CRYPTO_SECRET`/`CORS_ALLOWED_ORIGINS`/`JWT_SECRET`/`DB_PASSWORD`、老库执行迁移脚本。
