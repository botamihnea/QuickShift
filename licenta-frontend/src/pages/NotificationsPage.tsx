import axios from 'axios'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotifications, markNotificationRead } from '../api/notificationService'
import { useAuth } from '../auth/useAuth'
import type { NotificationItem } from '../types'
import './NotificationsPage.css'

function NotificationsPage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const loadNotifications = async () => {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const items = await getNotifications()
      setNotifications(items)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
        return
      }
      setErrorMessage('Could not load notifications right now. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadNotifications()
  }, [])

  return (
    <main className="notifications-shell">
      <section className="notifications-card">
        <header className="notifications-header">
          <div>
            <p className="notifications-brand">QuickShift</p>
            <h1>Notifications</h1>
            <p className="notifications-subtitle">Review new and past store updates.</p>
          </div>
          <div className="notifications-actions">
            <button type="button" className="notifications-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button
              type="button"
              className="notifications-secondary"
              onClick={() => {
                logout()
                navigate('/', { replace: true })
              }}
            >
              Log out
            </button>
          </div>
        </header>

        {errorMessage ? (
          <p className="notifications-status error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className="notifications-list">
          {isLoading ? (
            <p className="notifications-empty">Loading notifications...</p>
          ) : notifications.length === 0 ? (
            <p className="notifications-empty">No notifications yet.</p>
          ) : (
            notifications.map((item) => (
              <article
                key={item.id}
                className={item.read ? 'notification-card' : 'notification-card unread'}
              >
                <div>
                  <p className="notification-message">{item.message}</p>
                  <p className="notification-meta">
                    {item.storeName ? `${item.storeName} · ` : ''}
                    {new Date(item.createdAt).toLocaleString()}
                  </p>
                </div>
                {!item.read ? (
                  <button
                    type="button"
                    className="notification-mark"
                    onClick={async () => {
                      await markNotificationRead(item.id)
                      void loadNotifications()
                    }}
                  >
                    Mark read
                  </button>
                ) : (
                  <span className="notification-read">Read</span>
                )}
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  )
}

export default NotificationsPage
