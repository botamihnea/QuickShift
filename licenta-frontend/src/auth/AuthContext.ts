import { createContext } from 'react'

export type AuthContextValue = {
  token: string | null
  isAuthenticated: boolean
  setAuthToken: (token: string) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
