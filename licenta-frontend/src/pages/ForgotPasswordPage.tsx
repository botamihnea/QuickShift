import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../api/authService'
import './AuthPage.css'
import './ForgotPasswordPage.css'

function resolveForgotPasswordError(error: unknown): string {
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

  return 'Unable to send reset email right now. Please try again.'
}

function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitted, setIsSubmitted] = useState(false)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      await forgotPassword({ email: email.trim() })
      setIsSubmitted(true)
    } catch (error) {
      setErrorMessage(resolveForgotPasswordError(error))
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
        <h1>Reset your password</h1>
        <p className="auth-subtitle">Enter the email you use to sign in.</p>

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

          {isSubmitted ? (
            <p className="auth-success" role="status">
              If the email exists, a reset link is on its way.
            </p>
          ) : null}
          {errorMessage ? <p className="auth-error">{errorMessage}</p> : null}

          <button type="submit" className="auth-submit" disabled={isSubmitting}>
            {isSubmitting ? 'Sending...' : 'Send reset link'}
          </button>
        </form>

        <p className="auth-footer">
          Remembered your password? <Link to="/login">Log in</Link>
        </p>
      </section>
    </main>
  )
}

export default ForgotPasswordPage
