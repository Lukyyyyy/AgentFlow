import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import BrandLogo from '../components/BrandLogo';

/**
 * 登录页面
 */
const LoginPage = () => {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const result = await login(values);
      if (result.code === 200 && result.data) {
        message.success('登录成功');
        setAuth(result.data.token, result.data.refreshToken, result.data.user.username);
        navigate('/');
      } else {
        message.error(result.message || '登录失败');
      }
    } catch {
      message.error('登录失败,请检查网络连接');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-shell">
      <section className="login-story" aria-label="AgentFlow 产品介绍">
        <BrandLogo />
        <div className="login-story-copy">
          <h1>让智能体沿着清晰的路径运行</h1>
          <p>设计、调试并交付企业级 AI 工作流。每一个节点、变量与执行结果，都在同一个可视化空间中保持可控。</p>
        </div>
        <div className="login-flow-visual" aria-hidden="true">
          <span className="login-flow-node node-a">输入</span>
          <span className="login-flow-node node-b">智能体</span>
          <span className="login-flow-node node-c">知识</span>
          <span className="login-flow-node node-d">输出</span>
          <svg viewBox="0 0 640 240" preserveAspectRatio="none">
            <path d="M78 168 C180 168 174 70 286 70 S416 164 554 164" />
            <path d="M286 70 C350 70 360 205 451 205" />
          </svg>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="login-card-header">
            <BrandLogo />
            <h2>欢迎回来</h2>
            <p>登录工作空间，继续构建你的自动化流程。</p>
          </div>
        
        <Form
          name="login"
          onFinish={onFinish}
          size="large"
          className="login-form"
        >
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input 
              prefix={<UserOutlined />} 
              placeholder="用户名" 
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password 
              prefix={<LockOutlined />} 
              placeholder="密码" 
            />
          </Form.Item>

          <Form.Item className="login-submit-row">
            <Button 
              type="primary" 
              htmlType="submit" 
              className="w-full login-submit"
              loading={loading}
            >
              登录
            </Button>
          </Form.Item>
        </Form>
        </div>
      </section>
    </main>
  );
};

export default LoginPage;
