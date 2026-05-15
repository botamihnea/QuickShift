import axios from 'axios'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getNotifications, markNotificationRead } from '../api/notificationService'
import { approveLeaveRequest, denyLeaveRequest } from '../api/leaveService'
import { acknowledgeAbsence, generateScheduleForMonth } from '../api/shiftService'
import { useAuth } from '../auth/useAuth'
import type { AcknowledgeAbsenceResponse, LeaveRequestResponse, NotificationItem } from '../types'
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
  const [leaveActions, setLeaveActions] = useState<
    Record<
      number,
      {
        pending: boolean
        decision: 'approved' | 'denied' | null
        response: LeaveRequestResponse | null
        error: string | null
        denyOpen: boolean
        reason: string
        regeneratePending: boolean
        regenerateResult: string | null
      }
    >
  >({})

  const resolveYearMonth = (dateValue: string): { year: number; month: number } | null => {
    const parsed = new Date(dateValue)
    if (Number.isNaN(parsed.getTime())) return null
    return { year: parsed.getFullYear(), month: parsed.getMonth() + 1 }
  }

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

  const handleApproveLeave = async (notificationId: number, leaveRequestId: number) => {
    setLeaveActions((prev) => ({
      ...prev,
      [notificationId]: {
        pending: true,
        decision: null,
        response: null,
        error: null,
        denyOpen: false,
        reason: '',
        regeneratePending: false,
        regenerateResult: null,
      },
    }))

    try {
      const response = await approveLeaveRequest(leaveRequestId)
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          pending: false,
          decision: 'approved',
          response,
          error: null,
          denyOpen: false,
          reason: '',
          regeneratePending: false,
          regenerateResult: null,
        },
      }))
      await markNotificationRead(notificationId)
      void loadNotifications()
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not approve leave. Please try again.'
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          pending: false,
          decision: null,
          response: null,
          error: msg,
          denyOpen: false,
          reason: '',
          regeneratePending: false,
          regenerateResult: null,
        },
      }))
    }
  }

  const handleDenyLeave = async (notificationId: number, leaveRequestId: number, reason?: string) => {
    setLeaveActions((prev) => ({
      ...prev,
      [notificationId]: {
        ...(prev[notificationId] ?? {
          pending: false,
          decision: null,
          response: null,
          error: null,
          denyOpen: false,
          reason: '',
          regeneratePending: false,
          regenerateResult: null,
        }),
        pending: true,
        decision: null,
        error: null,
      },
    }))

    try {
      await denyLeaveRequest(leaveRequestId, reason ? { reason } : undefined)
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          pending: false,
          decision: 'denied',
          response: null,
          error: null,
          denyOpen: false,
          reason: '',
          regeneratePending: false,
          regenerateResult: null,
        },
      }))
      await markNotificationRead(notificationId)
      void loadNotifications()
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not deny leave. Please try again.'
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          ...(prev[notificationId] ?? {
            pending: false,
            decision: null,
            response: null,
            error: null,
            denyOpen: false,
            reason: '',
            regeneratePending: false,
            regenerateResult: null,
          }),
          pending: false,
          decision: null,
          error: msg,
        },
      }))
    }
  }

  const handleRegenerateForLeave = async (notificationId: number, response: LeaveRequestResponse) => {
    const resolved = resolveYearMonth(response.startDate)
    if (!resolved) {
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          ...(prev[notificationId] ?? {
            pending: false,
            decision: null,
            response: null,
            error: null,
            denyOpen: false,
            reason: '',
            regeneratePending: false,
            regenerateResult: null,
          }),
          regenerateResult: 'Could not determine month for regeneration.',
        },
      }))
      return
    }

    setLeaveActions((prev) => ({
      ...prev,
      [notificationId]: {
        ...(prev[notificationId] ?? {
          pending: false,
          decision: null,
          response,
          error: null,
          denyOpen: false,
          reason: '',
          regeneratePending: false,
          regenerateResult: null,
        }),
        regeneratePending: true,
        regenerateResult: null,
      },
    }))

    try {
      await generateScheduleForMonth(resolved.year, resolved.month)
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          ...(prev[notificationId] ?? {
            pending: false,
            decision: null,
            response,
            error: null,
            denyOpen: false,
            reason: '',
            regeneratePending: false,
            regenerateResult: null,
          }),
          regeneratePending: false,
          regenerateResult: 'Shifts regenerated for that month.',
        },
      }))
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not regenerate shifts right now.'
      setLeaveActions((prev) => ({
        ...prev,
        [notificationId]: {
          ...(prev[notificationId] ?? {
            pending: false,
            decision: null,
            response,
            error: null,
            denyOpen: false,
            reason: '',
            regeneratePending: false,
            regenerateResult: null,
          }),
          regeneratePending: false,
          regenerateResult: msg,
        },
      }))
    }
  }

  function renderActionArea(item: NotificationItem) {
    const ackState = acknowledgeResults[item.id]
    const leaveState = leaveActions[item.id]
    const isManager = currentUser?.role === 'MANAGER'
    // Show acknowledge button regardless of read-status — manager may have
    // clicked "Mark read" by mistake before the feature was in place
    const isActionable = item.relatedAbsenceRequestId != null && isManager
    const isLeaveActionable = item.relatedLeaveRequestId != null && isManager

    if (leaveState?.decision === 'approved') {
      return (
        <div className="leave-action-stack">
          <span className="ack-result success">✅ Leave approved</span>
          {leaveState.response ? (
            <button
              type="button"
              className="notification-acknowledge"
              disabled={leaveState.regeneratePending}
              onClick={() => handleRegenerateForLeave(item.id, leaveState.response!)}
            >
              {leaveState.regeneratePending ? 'Regenerating...' : 'Regenerate month'}
            </button>
          ) : null}
          {leaveState.regenerateResult ? (
            <span className="ack-result warning">{leaveState.regenerateResult}</span>
          ) : null}
        </div>
      )
    }

    if (leaveState?.decision === 'denied') {
      return <span className="ack-result warning">⚠️ Leave denied</span>
    }

    if (leaveState?.error) {
      return <span className="ack-result error">{leaveState.error}</span>
    }

    if (isLeaveActionable) {
      if (leaveState?.denyOpen) {
        return (
          <div className="leave-deny-panel">
            <label className="leave-deny-label" htmlFor={`leave-reason-${item.id}`}>
              Denial reason
            </label>
            <textarea
              id={`leave-reason-${item.id}`}
              className="leave-deny-input"
              rows={2}
              value={leaveState.reason}
              onChange={(event) =>
                setLeaveActions((prev) => ({
                  ...prev,
                  [item.id]: {
                    ...(prev[item.id] ?? {
                      pending: false,
                      decision: null,
                      response: null,
                      error: null,
                      denyOpen: true,
                      reason: '',
                      regeneratePending: false,
                      regenerateResult: null,
                    }),
                    reason: event.target.value,
                  },
                }))
              }
              placeholder="Optional reason"
              disabled={leaveState.pending}
            />
            <div className="leave-deny-actions">
              <button
                type="button"
                className="notification-acknowledge"
                disabled={leaveState.pending}
                onClick={() => handleDenyLeave(item.id, item.relatedLeaveRequestId!, leaveState.reason.trim())}
              >
                {leaveState.pending ? 'Sending...' : 'Confirm deny'}
              </button>
              <button
                type="button"
                className="notification-mark"
                disabled={leaveState.pending}
                onClick={() =>
                  setLeaveActions((prev) => ({
                    ...prev,
                    [item.id]: {
                      pending: false,
                      decision: null,
                      response: null,
                      error: null,
                      denyOpen: false,
                      reason: '',
                      regeneratePending: false,
                      regenerateResult: null,
                    },
                  }))
                }
              >
                Cancel
              </button>
            </div>
          </div>
        )
      }

      return (
        <div className="leave-action-buttons">
          <button
            type="button"
            className="notification-acknowledge"
            disabled={leaveState?.pending}
            onClick={() => handleApproveLeave(item.id, item.relatedLeaveRequestId!)}
          >
            {leaveState?.pending ? 'Processing...' : 'Approve leave'}
          </button>
          <button
            type="button"
            className="notification-mark"
            disabled={leaveState?.pending}
            onClick={() =>
              setLeaveActions((prev) => ({
                ...prev,
                [item.id]: {
                  pending: false,
                  decision: null,
                  response: null,
                  error: null,
                  denyOpen: true,
                  reason: prev[item.id]?.reason ?? '',
                  regeneratePending: false,
                  regenerateResult: null,
                },
              }))
            }
          >
            Deny
          </button>
        </div>
      )
    }

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
