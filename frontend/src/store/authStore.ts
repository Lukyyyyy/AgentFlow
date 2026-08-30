import { create } from 'zustand';
import { clearStoredAuth, getAccessToken, getRefreshToken, getUserEmail, setStoredAuth } from '../utils/auth';

interface AuthState {
  token: string | null;
  refreshToken: string | null;
  email: string | null;
  isAuthenticated: boolean;
  setAuth: (token: string, refreshToken: string, email: string) => void;
  clearAuth: () => void;
}

/**
 * 认证状态管理（登录仅支持邮箱，展示用邮箱标识用户）
 */
export const useAuthStore = create<AuthState>((set) => ({
  token: getAccessToken(),
  refreshToken: getRefreshToken(),
  email: getUserEmail(),
  isAuthenticated: !!getRefreshToken(),

  setAuth: (token: string, refreshToken: string, email: string) => {
    setStoredAuth(token, refreshToken, email);
    set({ token, refreshToken, email, isAuthenticated: true });
  },

  clearAuth: () => {
    clearStoredAuth();
    set({ token: null, refreshToken: null, email: null, isAuthenticated: false });
  },
}));
