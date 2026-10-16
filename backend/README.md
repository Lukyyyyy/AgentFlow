# AgentFlow Backend

AgentFlow 后端负责工作流持久化、执行引擎调度、节点运行、知识库、模型配置、MCP 工具与实时执行事件。

## 启动

```bash
cp .env.example .env
./mvnw spring-boot:run
```

默认端口为 `8084`，Swagger UI 位于 `http://localhost:8084/swagger-ui.html`。

## 主要模块

- `com.agentflow.controller`：认证、工作流、执行、知识库、技能和 MCP REST 接口。
- `com.agentflow.engine`：DAG、LangGraph4j、节点执行器、Agent 工具和技能加载。
- `com.agentflow.service`：业务服务及外部平台客户端。
- `com.agentflow.mapper`：MyBatis-Plus Mapper。
- `resources/schema.sql`：数据库结构与预置节点。

## 测试与构建

```bash
./mvnw test
./mvnw clean package
```

单测示例：

```bash
./mvnw test -Dtest=WorkflowConfigParserTest
./mvnw test -Dtest=ConditionNodeExecutorTest#methodName
```

## 配置

### LangGraph 条件分支与循环

- 在编辑器选择 `LangGraph 引擎`，添加“条件分支”节点并在右侧配置字段路径、比较方式和比较值。条件按顺序匹配，只执行第一个命中的出口；均不命中时走默认出口。没有连线的出口直接结束流程。
- 字段支持点分路径，也支持 JSON 字符串内的字段。例如 LLM 的 `output` 字段值为 JSON 文本 `{"score":60}`，可配置 `output.score < 80`。
- 将条件出口连回前面的节点可形成循环，默认出口可连接输出节点作为退出路径。每轮使用最新节点输出重新判断条件；循环体需要更新用于判断的字段。
- 工具栏的“步数上限”限制整次运行的节点执行总次数（包含输入、条件、循环体和输出节点），默认 `100`，范围 `1–10000`。超限会以 `FAILED` 结束并返回错误，节点执行失败也会立即停止。该设置保存在 `flowData.maxSteps`，旧工作流不需要迁移。
- 需要唯一输入节点；未配置输入节点时需要唯一无入边节点。多个分支可汇入同一个后继，所有无后继节点都可以结束流程。普通节点仅支持一个后继，分流请使用条件节点；不支持并行分流。
- 执行记录按实际顺序保留每轮节点结果；DAG 引擎仍不允许回边。

例如：`输入 → 条件(count < 3) → 循环体 → 条件`，条件的默认出口连接输出。循环体每轮输出更新后的 `count`，达到 3 后走默认出口结束。

配置模板见 [`.env.example`](.env.example)。生产环境必须显式设置数据库密码、强随机 `JWT_SECRET`，并关闭或配置默认普通账户。
