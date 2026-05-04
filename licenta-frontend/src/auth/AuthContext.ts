import { createContext } from 'react'
import type { AuthenticatedUser } from '../types'

export type AuthContextValue = {
  token: string | null
  currentUser: AuthenticatedUser | null
  isAuthenticated: boolean
  isAuthLoading: boolean
  isAdmin: boolean
  setAuthToken: (token: string) => void
  refreshCurrentUser: () => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
