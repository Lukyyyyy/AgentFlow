# AgentFlow Frontend

AgentFlow 前端是基于 React、TypeScript、Vite、React Flow 和 Ant Design 构建的可视化 AI 工作台。

## 开发

```bash
cp .env.example .env.local
npm install
npm run dev
```

默认访问地址为 `http://localhost:5173`。开发代理默认把 `/api` 转发到 `http://localhost:8084`。

## 页面与组件

- `pages/EditorPage.tsx`：工作流编排与节点配置。
- `pages/KnowledgePage.tsx`：知识库管理、分片、索引和检索测试。
- `pages/McpToolPage.tsx`：MCP 工具管理与调用测试。
- `components/FlowCanvas.tsx`：React Flow 画布和工作流节点。
- `components/DebugDrawer.tsx`：实时调试、日志和结果。
- `components/BrandLogo.tsx`：AgentFlow 矢量品牌标识。

## 质量检查

```bash
npm run lint
npm run build
```

全局视觉令牌与响应式规则位于 `src/index.css`。新增界面应沿用深海蓝黑背景、青蓝至靛紫品牌色、细边框和克制的动效语言。
