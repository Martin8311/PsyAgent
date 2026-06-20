# 🌉 MindBridge — 校园心理健康 AI 智能体系统

面向校园心理健康场景的 AI 智能体助手：**学生匿名倾诉 + 多模态情绪识别 + 智能咨询 + 高风险自动预警 + 数据记录**。

> 目标：解决「学生不敢咨询、老师顾不过来、风险无法及时发现」的痛点。
> 通过 *AI 辅助 + 人工兜底*，实现「全员覆盖、主动干预、隐私保护」。

## 技术栈

Spring Boot 3.3.5 · Java 17 · Spring WebFlux(SSE 流式) · Spring Data JPA · H2/MySQL · Redis · Chroma 向量库 · Spring Security · Ollama(本地/微调 qwen2.5) · MCP 工具协议

## 核心架构：Supervisor 路由式多 Agent 协作

```
MemoryAgent
 -> SupervisorAgent          // 根据意图分支
 -> 分支一：普通 chat(CHAT)
      CompanionAgent -> AiClient(Ollama/OpenAI) -> SSE 返回
 -> 分支二：心理咨询/风险(CONSULT/RISK)
      KnowledgeAgent -> RiskGuardianAgent -> CounselorAgent
        -> AiClient -> SSE 返回
        -> 生成心理报告 -> Excel 台账 / 高风险预警
```

`AgentRuntimeService` 统一调度，每个 Agent 通过 `supports(context)` 自判是否处理，
能处理就 `act(context)`，结果写回共享 `AgentContext`；loop 最多 `MAX_STEPS=8` 步防止失控。

## 实施进度

- [x] **Phase 1** Maven + Spring Boot 骨架 + AiClient 抽象 + Ollama 流式联通
- [ ] **Phase 2** 多 Agent 协作框架 (Supervisor 路由)
- [ ] **Phase 3** 记忆系统 (Redis 短期 + JPA 长期) + 安全权限
- [ ] **Phase 4** RAG 知识库 + 评测指标
- [ ] **Phase 5** 风险识别 + MCP(Excel 台账/邮件预警) + 前端

## 本地运行

### 前置
- JDK 17
- Ollama 服务运行中（`http://localhost:11434`），并已拉取模型：
  ```bash
  ollama pull qwen2.5:3b      # 测试用轻量模型
  ```
  模型名在 `application.yml` 的 `mindbridge.ai.ollama.model` 配置。

### 启动（命令行，推荐）

> 注意：若 IDEA 版本过老(< 2021.2)无法识别 `record`，请用命令行运行。
> 脚本已内置 `JAVA_HOME=D:\java17`。

```powershell
.\run.ps1            # 启动应用 (http://localhost:8080)
# 或双击 run.bat
.\build.ps1          # 仅编译
.\build.ps1 package  # 打包可执行 jar -> target\*.jar
```

启动后访问：
- 测试页：http://localhost:8080/
- 健康检查：http://localhost:8080/actuator/health
- 流式接口：`GET /api/chat/stream?message=你好`
