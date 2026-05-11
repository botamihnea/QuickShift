import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { resetPassword } from '../api/authService'
import './AuthPage.css'
import './ResetPasswordPage.css'

function resolveResetPasswordError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach backend right now. Please verify server/CORS setup and try again.'
    }

    if (typeof error.response.data === 'string' && error.response.data.trim().length > 0) {
      return error.response.data
    }

    const responseData = error.response.data as { message?: string } | undefined
    if (responseData?.message) {
      return responseData.message
    }
  }

  return 'Unable to reset password right now. Please try again.'
}

function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') ?? ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)

    if (!token) {
      setErrorMessage('Missing reset token. Please request a new link.')
      return
    }

    if (newPassword !== confirmPassword) {
      setErrorMessage('New passwords do not match.')
      return
    }

    setIsSubmitting(true)

    try {
      await resetPassword({ token, newPassword })
      navigate('/login', { replace: true, state: { passwordResetSuccess: true } })
    } catch (error) {
      setErrorMessage(resolveResetPasswordError(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <Link to="/login" className="auth-back-link">
          Back to login
        </Link>
        <p className="auth-brand">QuickShift</p>
        <h1>Choose a new password</h1>
        <p className="auth-subtitle">Make sure it is something you will remember.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            New password
            <input
              type="password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          <label>
            Confirm new password
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          {errorMessage ? <p className="auth-error">{errorMessage}</p> : null}

          <button type="submit" className="auth-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Reset password'}
          </button>
        </form>
      </section>
    </main>
  )
}

export default ResetPasswordPage
