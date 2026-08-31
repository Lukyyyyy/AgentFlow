import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Tabs, message } from 'antd';
import { LockOutlined, MailOutlined, SafetyOutlined } from '@ant-design/icons';
import { login, sendRegistrationCode, register } from '../api/auth';
import { useAuthStore } from '../store/authStore';
import BrandLogo from '../components/BrandLogo';

interface LoginValues {
  email: string;
  password: string;
}

interface RegisterValues {
  email: string;
  verificationCode: string;
  password: string;
}

/**
 * 登录/邮箱注册页面
 */
const LoginPage = () => {
  const [activeTab, setActiveTab] = useState<'login' | 'register'>('login');
  const [loginLoading, setLoginLoading] = useState(false);
  const [registerLoading, setRegisterLoading] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [registerForm] = Form.useForm();
  const navigate = useNavigate();
  const { setAuth } = useAuthStore();

  useEffect(() => {
    if (countdown <= 0) return undefined;
    const timer = setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [countdown]);

  const onLoginFinish = async (values: LoginValues) => {
    setLoginLoading(true);
    try {
      const result = await login(values);
      if (result.code === 200 && result.data) {
        message.success('登录成功');
        setAuth(result.data.token, result.data.refreshToken, result.data.user.email);
        navigate('/');
      } else {
        message.error(result.message || '登录失败');
      }
    } catch {
      message.error('登录失败,请检查网络连接');
    } finally {
      setLoginLoading(false);
    }
  };

  const onRegisterFinish = async (values: RegisterValues) => {
    setRegisterLoading(true);
    try {
      const result = await register(values);
      if (result.code === 200 && result.data) {
        message.success('注册成功');
        setAuth(result.data.token, result.data.refreshToken, result.data.user.email);
        navigate('/');
      } else {
        message.error(result.message || '注册失败');
      }
    } catch {
      message.error('注册失败,请检查网络连接');
    } finally {
      setRegisterLoading(false);
    }
  };

  const handleSendCode = async () => {
    try {
      await registerForm.validateFields(['email']);
    } catch {
      return;
    }
    setSendingCode(true);
    try {
      const result = await sendRegistrationCode({ email: registerForm.getFieldValue('email') });
      if (result.code === 200) {
        message.success('验证码已发送，请查收邮件（若未收到请检查垃圾箱）');
        setCountdown(60);
      } else {
        message.error(result.message || '验证码发送失败');
      }
    } catch {
      message.error('验证码发送失败，请检查网络连接');
    } finally {
      setSendingCode(false);
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
            <h2>{activeTab === 'login' ? '欢迎回来' : '创建账户'}</h2>
            <p>
              {activeTab === 'login'
                ? '登录工作空间，继续构建你的自动化流程。'
                : '使用邮箱注册，开始构建你的自动化流程。'}
            </p>
          </div>

          <Tabs
            activeKey={activeTab}
            onChange={(key) => setActiveTab(key as 'login' | 'register')}
            centered
            items={[
              {
                key: 'login',
                label: '登录',
                children: (
                  <Form
                    name="login"
                    onFinish={onLoginFinish}
                    size="large"
                    className="login-form"
                  >
                    <Form.Item
                      name="email"
                      rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '邮箱格式不正确' },
                      ]}
                    >
                      <Input
                        prefix={<MailOutlined />}
                        placeholder="邮箱"
                        autoComplete="email"
                      />
                    </Form.Item>

                    <Form.Item
                      name="password"
                      rules={[{ required: true, message: '请输入密码' }]}
                    >
                      <Input.Password
                        prefix={<LockOutlined />}
                        placeholder="密码"
                        autoComplete="current-password"
                      />
                    </Form.Item>

                    <Form.Item className="login-submit-row">
                      <Button
                        type="primary"
                        htmlType="submit"
                        className="w-full login-submit"
                        loading={loginLoading}
                      >
                        登录
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
              {
                key: 'register',
                label: '注册',
                children: (
                  <Form
                    form={registerForm}
                    name="register"
                    onFinish={onRegisterFinish}
                    size="large"
                    className="login-form"
                  >
                    <Form.Item
                      name="email"
                      rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '邮箱格式不正确' },
                      ]}
                    >
                      <Input
                        prefix={<MailOutlined />}
                        placeholder="邮箱"
                        autoComplete="email"
                      />
                    </Form.Item>

                    <Form.Item
                      name="verificationCode"
                      rules={[
                        { required: true, message: '请输入验证码' },
                        { pattern: /^\d{6}$/, message: '验证码为 6 位数字' },
                      ]}
                    >
                      <Input
                        prefix={<SafetyOutlined />}
                        placeholder="邮箱验证码"
                        maxLength={6}
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        suffix={
                          <Button
                            type="link"
                            size="small"
                            className="login-send-code"
                            disabled={countdown > 0}
                            loading={sendingCode}
                            onClick={handleSendCode}
                          >
                            {countdown > 0 ? `${countdown}s 后重发` : '获取验证码'}
                          </Button>
                        }
                      />
                    </Form.Item>

                    <Form.Item
                      name="password"
                      rules={[
                        { required: true, message: '请输入密码' },
                        { min: 8, message: '密码至少 8 个字符' },
                      ]}
                    >
                      <Input.Password
                        prefix={<LockOutlined />}
                        placeholder="密码"
                        autoComplete="new-password"
                      />
                    </Form.Item>

                    <Form.Item
                      name="confirmPassword"
                      dependencies={['password']}
                      rules={[
                        { required: true, message: '请再次输入密码' },
                        ({ getFieldValue }) => ({
                          validator(_, value) {
                            if (!value || getFieldValue('password') === value) {
                              return Promise.resolve();
                            }
                            return Promise.reject(new Error('两次输入的密码不一致'));
                          },
                        }),
                      ]}
                    >
                      <Input.Password
                        prefix={<LockOutlined />}
                        placeholder="确认密码"
                        autoComplete="new-password"
                      />
                    </Form.Item>

                    <Form.Item className="login-submit-row">
                      <Button
                        type="primary"
                        htmlType="submit"
                        className="w-full login-submit"
                        loading={registerLoading}
                      >
                        注册并登录
                      </Button>
                    </Form.Item>
                  </Form>
                ),
              },
            ]}
          />
        </div>
      </section>
    </main>
  );
};

export default LoginPage;
