import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login } from '../api/authService'
import { useAuth } from '../auth/useAuth'
import './AuthPage.css'

function resolveLoginError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach backend right now. Please verify server/CORS setup and try again.'
    }

    if (error.response.status === 401 || error.response.status === 403) {
      return 'The email or password is wrong.'
    }

    if (typeof error.response.data === 'string' && error.response.data.trim().length > 0) {
      return error.response.data
    }

    const responseData = error.response.data as { message?: string } | undefined
    if (responseData?.message) {
      return responseData.message
    }
  }

  return 'Login failed. Please check your credentials and try again.'
}

function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { setAuthToken } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      const response = await login({ email, password })
      setAuthToken(response.token)
      navigate('/schedule', { replace: true })
    } catch (error) {
      const message = resolveLoginError(error)
      setErrorMessage(message)

      if (message === 'The email or password is wrong.') {
        window.alert(message)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const passwordResetSuccess = Boolean(
    (location.state as { passwordResetSuccess?: boolean } | null)?.passwordResetSuccess,
  )

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <Link to="/" className="auth-back-link">
          Back to home
        </Link>
        <p className="auth-brand">QuickShift</p>
        <h1>Log in</h1>
        <p className="auth-subtitle">Access your shift planning dashboard.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              autoComplete="email"
            />
          </label>

          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoComplete="current-password"
            />
          </label>

          {passwordResetSuccess ? (
            <p className="auth-success" role="status">
              Your password has been reset. Please log in with the new password.
            </p>
          ) : null}
          {errorMessage ? <p className="auth-error">{errorMessage}</p> : null}

          <p className="auth-helper">
            <Link to="/forgot-password" className="auth-link-inline">
              Forgot password?
            </Link>
          </p>

          <button type="submit" className="auth-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Logging in...' : 'Log in'}
          </button>
        </form>

        <p className="auth-footer">
          Just employed? <Link to="/register">Register</Link>
        </p>
      </section>
    </main>
  )
}

export default LoginPage
