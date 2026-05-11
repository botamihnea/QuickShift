import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { changePassword } from '../api/authService'
import './AuthPage.css'
import './ChangePasswordPage.css'

function resolveChangePasswordError(error: unknown): string {
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

  return 'Unable to change password right now. Please try again.'
}

function ChangePasswordPage() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setSuccessMessage(null)

    if (newPassword !== confirmPassword) {
      setErrorMessage('New passwords do not match.')
      return
    }

    setIsSubmitting(true)

    try {
      await changePassword({ currentPassword, newPassword })
      setSuccessMessage('Password updated successfully.')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (error) {
      setErrorMessage(resolveChangePasswordError(error))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <Link to="/schedule" className="auth-back-link">
          Back to schedule
        </Link>
        <p className="auth-brand">QuickShift</p>
        <h1>Change password</h1>
        <p className="auth-subtitle">Update the password for your account.</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Current password
            <input
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              required
              autoComplete="current-password"
            />
          </label>

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

          {successMessage ? (
            <p className="auth-success" role="status">
              {successMessage}
            </p>
          ) : null}
          {errorMessage ? <p className="auth-error">{errorMessage}</p> : null}

          <button type="submit" className="auth-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Saving...' : 'Update password'}
          </button>
        </form>
      </section>
    </main>
  )
}

export default ChangePasswordPage
