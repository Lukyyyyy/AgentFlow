import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import EditorPage from './pages/EditorPage';
import KnowledgePage from './pages/KnowledgePage';
import McpToolPage from './pages/McpToolPage';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';

const theme = {
  token: {
    colorPrimary: '#4f6bed',
    colorInfo: '#4f6bed',
    colorSuccess: '#20a36a',
    colorWarning: '#d58a14',
    colorError: '#d94a5c',
    colorBgBase: '#f6f8fb',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBorder: '#dce3ec',
    colorBorderSecondary: '#e8edf3',
    colorText: '#172033',
    colorTextSecondary: '#5e6b7c',
    borderRadius: 8,
    borderRadiusLG: 10,
    controlHeight: 36,
    fontFamily: 'Inter, "PingFang SC", "Microsoft YaHei", system-ui, sans-serif',
  },
};

function App() {
  return (
    <ConfigProvider locale={zhCN} theme={theme}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/editor" element={<EditorPage />} />
          <Route path="/editor/:id" element={<EditorPage />} />
          <Route path="/knowledge" element={<KnowledgePage />} />
          <Route path="/mcp-tools" element={<McpToolPage />} />
          <Route path="/" element={<Navigate to="/editor" replace />} />
          <Route path="*" element={<Navigate to="/editor" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}

export default App;
