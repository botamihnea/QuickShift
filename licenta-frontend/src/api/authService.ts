import httpClient from './httpClient'
import type { AuthRequest, AuthResponse, AuthenticatedUser, RegisterRequest } from '../types'

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
