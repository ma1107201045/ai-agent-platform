# ai-agent-platform（智能体平台）

自研的 Dify / Coze 类 LLM 智能体平台，覆盖「模型接入 → 知识库 → 工具 → 工作流编排 → 应用发布 → 对外对话」完整闭环。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.2、MyBatis-Plus 3.5、MVEL2（沙箱化）、Maven 多模块 |
| 前端 | Vue 3.4、TypeScript、Vite 5、Element Plus、Vue Flow（画布编排）、Pinia |
| 数据库 | PostgreSQL 16+（pgvector 扩展，52 张表） |

## 模块结构

```
agent-common       通用层：Result/异常、JWT、UserContext、SafeMvel 沙箱、滑动窗口限流器
agent-llm          模型 SPI：Chat/Embedding/Rerank 模型抽象与各厂商实现、模型网关路由
agent-dao          实体与 Mapper（51+ 张表）
agent-orchestrator 工作流引擎：DAG 编排、节点 Schema 单源化、SSE 运行监控、DSL v2 变量渲染
agent-service      业务服务：应用/知识库/工具/团队/评测/内容安全/记忆/发布渠道等
agent-web          接口层与启动模块：39 个 Controller、全局租户拦截器、门户限流
frontend           Vue 3 前端（18 个视图分组）
sql                schema.sql（幂等建表）/ data.sql（种子数据）/ migration/（增量补丁）
docs               设计文档与改造交付说明
```

## 本地启动

### 前置要求

- JDK 21（**注意**：JDK 27 下 Lombok 注解处理失效，请勿使用）
- Maven 3.9+
- Node.js 22 + pnpm
- PostgreSQL 16+（启用 pgvector 扩展）

### 1. 初始化数据库

```bash
psql -U postgres -c "CREATE DATABASE agent_platform"
psql -U postgres -d agent_platform -f sql/schema.sql   # 首行自动启用 pgvector
psql -U postgres -d agent_platform -f sql/data.sql     # 可选：种子数据
```

两个脚本均为幂等（`CREATE TABLE IF NOT EXISTS` / `ON CONFLICT DO NOTHING`），可重复执行。
后续结构变更走 `sql/migration/` 增量补丁，约定见 `sql/README.md`。

### 2. 配置环境变量

复制 `.env.example` 为 `.env`（已 gitignore），至少填写：

```bash
DB_PASSWORD=你的数据库密码
JWT_SECRET=$(openssl rand -base64 48)   # >=32 字节，必填，缺失时启动直接报错
```

### 3. 启动后端

```bash
mvn spring-boot:run -pl agent-web -am
# 或先打包再运行
mvn package -DskipTests && java -jar agent-web/target/agent-web-0.1.0.jar
```

后端默认端口 8080。

### 4. 启动前端

```bash
cd frontend
pnpm install
pnpm dev        # http://localhost:5173 ，/api 已代理到 8080
```

## 测试

```bash
mvn test        # 全模块：引擎 DAG/变量渲染、SafeMvel 沙箱、限流器、知识库分块/检索/缓存等
```

## 安全设计要点

- **密钥外置**：`application.yml` 不含任何真实密钥，全部走环境变量（见 `.env.example`）
- **多租户**：MyBatis-Plus `TenantLineInnerInterceptor` 全局自动追加 `tenant_id` 条件
- **MVEL 沙箱**：所有用户脚本经 `SafeMvel` 统一执行，阻断反射/IO/线程/任意类实例化
- **门户限流**：`/api/portal/public/**` 匿名 30 次/分钟/IP，API Key 120 次/分钟（可调）

## 文档

- `docs/project-analysis.md` —— 项目分析报告（2026-09 快照，部分已过时）
- `docs/orchestrator-改造交付说明.md` —— 工作流引擎改造说明
- `sql/README.md` —— 数据库脚本使用与变更约定
