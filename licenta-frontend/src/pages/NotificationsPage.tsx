import axios from 'axios'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotifications, markNotificationRead } from '../api/notificationService'
import { acknowledgeAbsence } from '../api/shiftService'
import { useAuth } from '../auth/useAuth'
import type { AcknowledgeAbsenceResponse, NotificationItem } from '../types'
import './NotificationsPage.css'

function NotificationsPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  // Track per-notification acknowledge result
  const [acknowledgeResults, setAcknowledgeResults] = useState<
    Record<number, { pending: boolean; result: AcknowledgeAbsenceResponse | null; error: string | null }>
  >({})

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

  const handleAcknowledge = async (notificationId: number, absenceRequestId: number) => {
    setAcknowledgeResults((prev) => ({
      ...prev,
      [notificationId]: { pending: true, result: null, error: null },
    }))

    try {
      const result = await acknowledgeAbsence(absenceRequestId)
      setAcknowledgeResults((prev) => ({
        ...prev,
        [notificationId]: { pending: false, result, error: null },
      }))
      // Mark the notification as read and refresh so the main schedule reflects the change
      await markNotificationRead(notificationId)
      void loadNotifications()
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not process the request. Please try again.'
      setAcknowledgeResults((prev) => ({
        ...prev,
        [notificationId]: { pending: false, result: null, error: msg },
      }))
    }
  }

  function renderActionArea(item: NotificationItem) {
    const ackState = acknowledgeResults[item.id]
    const isManager = currentUser?.role === 'MANAGER'
    // Show acknowledge button regardless of read-status — manager may have
    // clicked "Mark read" by mistake before the feature was in place
    const isActionable = item.relatedAbsenceRequestId != null && isManager

    // Show acknowledge result feedback
    if (ackState?.result) {
      return ackState.result.replacementFound ? (
        <span className="ack-result success">
          ✅ {ackState.result.replacementEmployeeName} assigned
        </span>
      ) : (
        <span className="ack-result warning">⚠️ No replacement found</span>
      )
    }

    if (ackState?.error) {
      return <span className="ack-result error">{ackState.error}</span>
    }

    if (isActionable) {
      return (
        <button
          type="button"
          className="notification-acknowledge"
          disabled={ackState?.pending}
          onClick={() => handleAcknowledge(item.id, item.relatedAbsenceRequestId!)}
        >
          {ackState?.pending ? 'Processing...' : 'Acknowledge & Replace'}
        </button>
      )
    }

    if (!item.read) {
      return (
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
      )
    }

    return <span className="notification-read">Read</span>
  }

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
                {renderActionArea(item)}
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  )
}

export default NotificationsPage
