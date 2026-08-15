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

配置模板见 [`.env.example`](.env.example)。生产环境必须显式设置数据库密码、强随机 `JWT_SECRET`，并关闭或修改默认管理员账户。
