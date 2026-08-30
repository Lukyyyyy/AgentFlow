import api from '../utils/request';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  user: {
    username: string;
    email: string;
  };
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface RegistrationCodeRequest {
  email: string;
}

export interface RegisterRequest {
  email: string;
  verificationCode: string;
  password: string;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * 用户登录
 */
export const login = (data: LoginRequest): Promise<ApiResult<LoginResponse>> => {
  return api.post('/api/auth/login', data);
};

/**
 * 发送注册验证码
 */
export const sendRegistrationCode = (data: RegistrationCodeRequest): Promise<ApiResult<void>> => {
  return api.post('/api/auth/registration-code', data);
};

/**
 * 邮箱注册（成功后自动登录）
 */
export const register = (data: RegisterRequest): Promise<ApiResult<LoginResponse>> => {
  return api.post('/api/auth/register', data);
};

/**
 * 用户登出
 */
export const logout = (data?: RefreshTokenRequest): Promise<ApiResult<void>> => {
  return api.post('/api/auth/logout', data);
};

/**
 * 刷新访问令牌
 */
export const refreshToken = (data: RefreshTokenRequest): Promise<ApiResult<LoginResponse>> => {
  return api.post('/api/auth/refresh', data);
};

/**
 * 获取当前用户信息
 */
export const getCurrentUser = (): Promise<ApiResult<{ username: string; authenticated: boolean }>> => {
  return api.get('/api/auth/current');
};
