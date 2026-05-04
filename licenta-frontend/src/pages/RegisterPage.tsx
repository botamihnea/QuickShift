import axios from 'axios'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register } from '../api/authService'
import { getStores } from '../api/storeService'
import { useAuth } from '../auth/useAuth'
import type { StoreSummary } from '../types'
import './AuthPage.css'

function RegisterPage() {
  const navigate = useNavigate()
  const { setAuthToken } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [selectedStoreId, setSelectedStoreId] = useState('')
  const [stores, setStores] = useState<StoreSummary[]>([])
  const [isLoadingStores, setIsLoadingStores] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    let isCancelled = false

    const loadStores = async () => {
      setIsLoadingStores(true)

      try {
        const fetchedStores = await getStores()

        if (isCancelled) {
          return
        }

        setStores(fetchedStores)

        if (fetchedStores.length === 1) {
          setSelectedStoreId(String(fetchedStores[0].id))
        }
      } catch {
        if (!isCancelled) {
          setErrorMessage('Could not load stores. Please refresh and try again.')
        }
      } finally {
        if (!isCancelled) {
          setIsLoadingStores(false)
        }
      }
    }

    void loadStores()

    return () => {
      isCancelled = true
    }
  }, [])

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)

    if (password !== confirmPassword) {
      setErrorMessage('Passwords do not match.')
      return
    }

    if (!selectedStoreId) {
      setErrorMessage('Please choose your store.')
      return
    }

    setIsSubmitting(true)

    try {
      const response = await register({
        email,
        password,
        storeId: Number(selectedStoreId),
      })
      setAuthToken(response.token)
      navigate('/schedule', { replace: true })
    } catch (error) {
      if (axios.isAxiosError(error) && typeof error.response?.data === 'string') {
        setErrorMessage(error.response.data)
      } else {
        setErrorMessage('Registration failed. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-shell">
      <section className="auth-card">
        <p className="auth-brand">QuickShift</p>
        <h1>Create account</h1>
        <p className="auth-subtitle">Register to start managing shift generation.</p>

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
            Store
            <select
              value={selectedStoreId}
              onChange={(event) => setSelectedStoreId(event.target.value)}
              required
              disabled={isLoadingStores || stores.length === 0}
            >
              <option value="">
                {isLoadingStores
                  ? 'Loading stores...'
                  : stores.length === 0
                    ? 'No stores available'
                    : 'Select your store'}
              </option>
              {stores.map((store) => (
                <option key={store.id} value={store.id}>
                  {store.storeName}
                </option>
              ))}
            </select>
          </label>

          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          <label>
            Confirm password
            <input
              type="password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
              autoComplete="new-password"
            />
          </label>

          {errorMessage ? <p className="auth-error">{errorMessage}</p> : null}

          <button
            type="submit"
            className="auth-submit"
            disabled={isSubmitting || isLoadingStores || stores.length === 0}
          >
            {isLoadingStores ? 'Loading stores...' : isSubmitting ? 'Creating account...' : 'Register'}
          </button>
        </form>

        <p className="auth-footer">
          Already registered? <Link to="/login">Log in</Link>
        </p>
      </section>
    </main>
  )
}

export default RegisterPage
