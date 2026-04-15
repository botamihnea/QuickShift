import httpClient from './httpClient'
import type { AuthRequest, AuthResponse } from '../types'

export async function login(request: AuthRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<AuthResponse>('/api/auth/login', request)
  return data
}

export async function register(request: AuthRequest): Promise<AuthResponse> {
  const { data } = await httpClient.post<AuthResponse>('/api/auth/register', request)
  return data
}
