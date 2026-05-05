import axios from 'axios'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { createStore, getStoreStaff, getStores } from '../api/storeService'
import { useAuth } from '../auth/useAuth'
import type { StoreStaffResponse, StoreSummary } from '../types'
import './StoreManagementPage.css'

function resolveCreateStoreError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return 'Cannot reach backend right now. Please verify server/CORS setup and try again.'
    }

    if (error.response.status === 401) {
      return 'Your session expired. Please log in again.'
    }

    if (error.response.status === 403) {
      return 'Only administrators are allowed to create stores.'
    }

    if (typeof error.response.data === 'string' && error.response.data.trim().length > 0) {
      return error.response.data
    }

    const responseData = error.response.data as { message?: string } | undefined
    if (responseData?.message) {
      return responseData.message
    }
  }

  return 'Could not create store. Please check your inputs and try again.'
}

function resolveLoadStoresError(error: unknown): string {
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

  return 'Could not load stores right now. Please try again.'
}

function StoreManagementPage() {
  const navigate = useNavigate()
  const { isAdmin, isAuthLoading, logout } = useAuth()

  const [storeName, setStoreName] = useState('')
  const [address, setAddress] = useState('')
  const [busyDaySalesThreshold, setBusyDaySalesThreshold] = useState('2000')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isStoresVisible, setIsStoresVisible] = useState(false)
  const [isLoadingStores, setIsLoadingStores] = useState(false)
  const [stores, setStores] = useState<StoreSummary[]>([])
  const [storesErrorMessage, setStoresErrorMessage] = useState<string | null>(null)
  const [staffStoreId, setStaffStoreId] = useState<number | null>(null)
  const [staffInfo, setStaffInfo] = useState<StoreStaffResponse | null>(null)
  const [isLoadingStaff, setIsLoadingStaff] = useState(false)
  const [staffErrorMessage, setStaffErrorMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const loadStores = async () => {
    setIsLoadingStores(true)
    setStoresErrorMessage(null)

    try {
      const allStores = await getStores()
      setStores(allStores)
    } catch (error) {
      setStoresErrorMessage(resolveLoadStoresError(error))
    } finally {
      setIsLoadingStores(false)
    }
  }

  const handleToggleStores = async () => {
    if (isStoresVisible) {
      setIsStoresVisible(false)
      return
    }

    setIsStoresVisible(true)
    await loadStores()
  }

  const loadStoreStaff = async (storeId: number) => {
    setIsLoadingStaff(true)
    setStaffErrorMessage(null)
    setStaffStoreId(storeId)

    try {
      const data = await getStoreStaff(storeId)
      setStaffInfo(data)
    } catch {
      setStaffErrorMessage('Could not load staff for this store.')
      setStaffInfo(null)
    } finally {
      setIsLoadingStaff(false)
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setErrorMessage(null)
    setSuccessMessage(null)

    const parsedThreshold = Number(busyDaySalesThreshold)
    if (!Number.isFinite(parsedThreshold) || parsedThreshold <= 0) {
      setErrorMessage('Busy day sales threshold must be a positive number.')
      return
    }

    setIsSubmitting(true)

    try {
      const createdStore = await createStore({
        storeName: storeName.trim(),
        address: address.trim(),
        busyDaySalesThreshold: parsedThreshold,
      })

      setSuccessMessage(`Store "${createdStore.storeName}" was created successfully.`)
      setStoreName('')
      setAddress('')
      setBusyDaySalesThreshold('2000')

      if (isStoresVisible) {
        await loadStores()
      }
    } catch (error) {
      const message = resolveCreateStoreError(error)
      setErrorMessage(message)

      if (message === 'Your session expired. Please log in again.') {
        logout()
        navigate('/login', { replace: true })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isAuthLoading) {
    return (
      <main className="store-admin-shell">
        <section className="store-admin-card">
          <p className="store-admin-status">Loading account permissions...</p>
        </section>
      </main>
    )
  }

  if (!isAdmin) {
    return (
      <main className="store-admin-shell">
        <section className="store-admin-card">
          <p className="store-admin-brand">QuickShift</p>
          <h1>Store management</h1>
          <p className="store-admin-status error" role="alert">
            Only administrators can access this page.
          </p>
          <button
            type="button"
            className="store-admin-secondary"
            onClick={() => navigate('/schedule', { replace: true })}
          >
            Back to schedule
          </button>
        </section>
      </main>
    )
  }

  return (
    <main className="store-admin-shell">
      <section className="store-admin-card">
        <div className="store-admin-header">
          <div>
            <p className="store-admin-brand">QuickShift</p>
            <h1>Store management</h1>
            <p className="store-admin-subtitle">
              Admin dashboard area. Create a new store that can be selected during account
              registration.
            </p>
          </div>

          <div className="store-admin-actions">
            <button type="button" className="store-admin-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button
              type="button"
              className="store-admin-secondary"
              onClick={() => {
                logout()
                navigate('/', { replace: true })
              }}
            >
              Log out
            </button>
          </div>
        </div>

        <form className="store-admin-form" onSubmit={handleSubmit}>
          <div className="store-admin-layout">
            <section className="store-admin-panel">
              <h2>Create a new store</h2>

              <label>
                Store name
                <input
                  type="text"
                  value={storeName}
                  onChange={(event) => setStoreName(event.target.value)}
                  required
                  maxLength={120}
                  placeholder="City Center"
                />
              </label>

              <label>
                Address
                <input
                  type="text"
                  value={address}
                  onChange={(event) => setAddress(event.target.value)}
                  required
                  maxLength={200}
                  placeholder="Street, number, city"
                />
              </label>

              <label>
                Busy day sales threshold
                <input
                  type="number"
                  value={busyDaySalesThreshold}
                  onChange={(event) => setBusyDaySalesThreshold(event.target.value)}
                  required
                  min={50}
                  step={50}
                />
                <span className="store-admin-hint">Use arrows to increase/decrease by 50 units.</span>
              </label>

              {errorMessage ? (
                <p className="store-admin-status error" role="alert">
                  {errorMessage}
                </p>
              ) : null}

              {successMessage ? (
                <p className="store-admin-status success" role="status">
                  {successMessage}
                </p>
              ) : null}

              <button type="submit" className="store-admin-submit" disabled={isSubmitting}>
                {isSubmitting ? 'Creating store...' : 'Create store'}
              </button>
            </section>

            <aside className="store-list-panel" aria-live="polite">
              <div className="store-list-header">
                <h2>All stores</h2>
                <button
                  type="button"
                  className="store-list-toggle"
                  onClick={() => {
                    void handleToggleStores()
                  }}
                  disabled={isLoadingStores}
                >
                  {isStoresVisible
                    ? 'Hide stores'
                    : isLoadingStores
                      ? 'Loading stores...'
                      : 'View all stores'}
                </button>
              </div>

              {!isStoresVisible ? (
                <p className="store-list-empty">Open this panel to view every registered store.</p>
              ) : isLoadingStores ? (
                <p className="store-list-empty">Loading stores...</p>
              ) : storesErrorMessage ? (
                <p className="store-admin-status error" role="alert">
                  {storesErrorMessage}
                </p>
              ) : stores.length === 0 ? (
                <p className="store-list-empty">No stores found yet.</p>
              ) : (
                <ul className="store-list-grid">
                  {stores.map((store, index) => (
                    <li className="store-list-item" key={store.id}>
                      <p className="store-list-order">#{String(index + 1).padStart(2, '0')}</p>
                      <p className="store-list-name">{store.storeName}</p>
                      <p className="store-list-id">Store ID: {store.id}</p>
                      <button
                        type="button"
                        className="store-list-action"
                        onClick={() => {
                          void loadStoreStaff(store.id)
                        }}
                      >
                        View staff
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </aside>
            <aside className="store-list-panel">
              <div className="store-list-header">
                <h2>Store staff</h2>
              </div>
              {staffStoreId === null ? (
                <p className="store-list-empty">Select a store to view its manager and employees.</p>
              ) : isLoadingStaff ? (
                <p className="store-list-empty">Loading staff details...</p>
              ) : staffErrorMessage ? (
                <p className="store-admin-status error" role="alert">
                  {staffErrorMessage}
                </p>
              ) : staffInfo ? (
                <div className="staff-summary">
                  <div className="staff-section">
                    <p className="staff-label">Manager</p>
                    <p className="staff-value">
                      {staffInfo.manager ? staffInfo.manager.email : 'No manager assigned'}
                    </p>
                  </div>
                  <div className="staff-section">
                    <p className="staff-label">Employees</p>
                    {staffInfo.employees.length === 0 ? (
                      <p className="staff-value">No employees yet.</p>
                    ) : (
                      <ul className="staff-list">
                        {staffInfo.employees.map((employee) => (
                          <li key={employee.id}>
                            {employee.fullName} · {employee.contractType.replace('_', ' ')} · {employee.shiftPreference}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>
                </div>
              ) : null}
            </aside>
          </div>
        </form>
      </section>
    </main>
  )
}

export default StoreManagementPage