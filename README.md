# 🌉 MindBridge — 校园心理健康 AI 智能体系统

面向校园心理健康场景的 AI 智能体助手：**学生匿名倾诉 + 多会话隔离 + RAG 知识库 + 智能咨询 + 高风险自动预警 + 数据台账**。

> 目标：解决「学生不敢咨询、老师顾不过来、风险无法及时发现」的痛点。
> 通过 *AI 辅助 + 人工兜底*，实现「全员覆盖、主动干预、隐私保护」。

## 技术栈

Spring Boot 3.4.13 · Java 17 · Spring WebFlux(SSE 流式) · Spring Data JPA · MySQL · Redis(响应式) · Chroma 向量库 · Spring AI 1.0.9 · Ollama(qwen2.5 对话 / bge-m3 向量) · RabbitMQ(异步任务) · Spring Mail(QQ SMTP) · Apache POI(Excel) · MCP Server(SSE) · Spring Security

## 核心架构：Supervisor 路由式多 Agent 协作

```
MemoryAgent                       // 从 Redis 加载会话短期记忆
 -> SupervisorAgent               // 按意图分支
 -> 分支一：普通闲聊(CHAT)
      CompanionAgent -> AiClient(Ollama) -> SSE 流式返回
 -> 分支二：心理咨询/风险(CONSULT/RISK)
      KnowledgeAgent(RAG 检索) -> RiskGuardianAgent(风险研判) -> CounselorAgent
        -> AiClient -> SSE 流式返回
        -> 高危则落库预警 + 异步邮件
```

`AgentRuntimeService` 统一调度，每个 Agent 通过 `supports(context)` 自判是否处理，
结果写回共享 `AgentContext`；loop 最多 `MAX_STEPS=8` 步防止失控。

## 异步任务与 MCP（事件驱动）

写 Excel、发邮件等阻塞副作用通过 **RabbitMQ** 异步化，不阻塞对话主流程；
同一套能力又以 **MCP 工具**对外暴露，供外部 AI 客户端调用。

```
对话完成 ──┐
高危命中 ──┤→ RabbitMQ(Topic Exchange, 独立 vhost) ──┬→ q.chat.log       → 写 Excel 台账(POI)
          │                                          ├→ q.risk.alert     → 邮件通知管理员(第一跳)
          │                                          └→ q.guardian.notify→ 邮件通知监护人(第二跳)
MCP Server(SSE /sse) ── appendChatLog / sendAlertEmail / queryRecentAlerts(复用同一 Service 层)
```

- **对话台账**：按「用户 + 会话」分文件写入 `data/chat-logs/{用户}_session{N}.xlsx`，后台可下载。
- **高危两段式邮件**：命中高危 → 自动邮件通知**管理员/心理老师**(待核实) → 管理员后台核实后**一键通知监护人**。误报可标记忽略，避免惊扰家长。每队列挂死信队列(DLQ)兜底。
- **MCP Server**：`spring-ai-starter-mcp-server-webflux`，SSE 端点 `/sse`，外部 Claude Desktop 等可连接调用工具。

## 功能一览

- 多会话隔离：每账号多个会话，独立历史，按日期分组 + 关键词搜索
- RAG 知识库：上传 txt/md → 切块 → bge-m3 向量化 → Chroma 检索注入
- 风险识别：高风险词硬规则 + LLM 结构化研判 + 关键词兜底三层策略
- 管理后台：预警台账、学生/监护人管理(单条编辑 + CSV 批量导入)、对话台账下载、知识库管理

## 实施进度

- [x] **Phase 1** Maven + Spring Boot 骨架 + AiClient 抽象 + Ollama 流式联通
- [x] **Phase 2** 多 Agent 协作框架 (Supervisor 路由)
- [x] **Phase 3** 记忆系统 (Redis 短期 + JPA 长期) + 安全权限
- [x] **Phase 4** RAG 知识库 (Chroma + bge-m3 + Spring AI)
- [x] **Phase 5** 多会话系统 (每账号独立隔离会话 + 持久化历史)
- [x] **Phase 6** 异步任务 (RabbitMQ) + 真 MCP Server + 两段式高危邮件

## 本地运行

### 前置依赖

| 组件 | 说明 |
|---|---|
| **JDK 17** | `JAVA_HOME` 指向 17 |
| **Ollama** | `http://localhost:11434`，`ollama pull qwen2.5:3b` 与 `ollama pull bge-m3` |
| **MySQL** | 库 `mindbridge`(utf8mb4，自动建表) |
| **Redis** | 会话短期记忆 |
| **Chroma** | `docker run -d -p 8000:8000 chromadb/chroma`（RAG 向量库） |
| **RabbitMQ** | `docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management`，建独立 vhost `mindbridge` |
| **QQ 邮箱** | 开启 SMTP，获取**授权码**(非登录密码)，用于高危预警邮件 |

### 配置凭据（不进仓库）

所有密码走环境变量，集中在 **gitignored** 的本地脚本里：

- Windows：复制 `env-local.bat`，填入 `MYSQL_PASSWORD` / `REDIS_PASSWORD` / `RABBITMQ_*` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `ADMIN_MAIL`
- 该文件已在 `.gitignore`，**切勿提交**

### 启动

```powershell
.\run.bat        # 或 .\run.ps1 (PowerShell)
```

`run.bat` 会自动加载 `env-local.bat` 凭据、拉起 RabbitMQ 容器、启动应用。

> IDEA 版本过老(< 2021.2)无法识别 `record`，请用命令行运行；脚本已内置 `JAVA_HOME=D:\java17`。

### 访问

- 学生端：http://localhost:8080/ （`student / student123`）
- 管理后台：http://localhost:8080/admin.html （`admin / admin123`）
- 聊天接口：`POST /api/chat`（SSE 流式，body: `{"message":"...","sessionId":N}`）
- MCP 端点：`http://localhost:8080/sse`
- 健康检查：http://localhost:8080/actuator/health
