import httpClient from './httpClient'
import type {
  AuthRequest,
  AuthResponse,
  AuthenticatedUser,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  RegisterRequest,
  ResetPasswordRequest,
} from '../types'

export async function login(request: AuthRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<AuthResponse>('/api/auth/login', request)
  return data
}

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<AuthResponse>('/api/auth/register', request)
  return data
}

export async function getCurrentUser(): Promise<AuthenticatedUser> {
  const { data } = await httpClient.get<AuthenticatedUser>('/api/auth/me')
  return data
}

export async function forgotPassword(request: ForgotPasswordRequest): Promise<void> {
  await httpClient.post('/api/auth/forgot-password', request)
}

export async function resetPassword(request: ResetPasswordRequest): Promise<void> {
  await httpClient.post('/api/auth/reset-password', request)
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await httpClient.put('/api/auth/change-password', request)
}
