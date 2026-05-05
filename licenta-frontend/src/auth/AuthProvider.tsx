import axios from 'axios'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getCurrentUser } from '../api/authService'
import type { AuthenticatedUser } from '../types'
import { AuthContext, type AuthContextValue } from './AuthContext'
import { clearStoredToken, getStoredToken, setStoredToken } from './tokenStorage'

type AuthProviderProps = {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [token, setToken] = useState<string | null>(getStoredToken())
  const [currentUser, setCurrentUser] = useState<AuthenticatedUser | null>(null)
  const [isAuthLoading, setIsAuthLoading] = useState<boolean>(Boolean(token))

  const refreshCurrentUser = useCallback(async () => {
    if (!token) {
      setCurrentUser(null)
      setIsAuthLoading(false)
      return
    }

    setIsAuthLoading(true)

    try {
      const user = await getCurrentUser()
      setCurrentUser(user)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        clearStoredToken()
        setToken(null)
      }
      setCurrentUser(null)
    } finally {
      setIsAuthLoading(false)
    }
  }, [token])

  useEffect(() => {
    void refreshCurrentUser()
  }, [refreshCurrentUser])

  const value = useMemo<AuthContextValue>(
    () => ({
      token,
      currentUser,
      isAuthenticated: Boolean(currentUser),
      isAuthLoading,
      isAdmin: currentUser?.role === 'ADMIN',
      setAuthToken: (nextToken: string) => {
        setStoredToken(nextToken)
        setCurrentUser(null)
        setIsAuthLoading(true)
        setToken(nextToken)
      },
      refreshCurrentUser,
      logout: () => {
        clearStoredToken()
        setCurrentUser(null)
        setIsAuthLoading(false)
        setToken(null)
      },
    }),
    [currentUser, isAuthLoading, refreshCurrentUser, token],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
