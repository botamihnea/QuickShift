import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMyShifts, reportAbsence } from '../api/shiftService'
import { useAuth } from '../auth/useAuth'
import type { BackendShift } from '../types'
import './MyShiftsPage.css'

type ShiftDateValue = string | [number, number, number] | { year: number; month: number; day: number }

function toLocalDate(value: ShiftDateValue): Date {
  if (Array.isArray(value)) {
    const [year, month, day] = value
    return new Date(year, month - 1, day)
  }

  if (typeof value === 'object') {
    return new Date(value.year, value.month - 1, value.day)
  }

  const parsed = new Date(value)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed
  }

  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

function parseShiftHours(shiftType: string): { startHour: number; endHour: number } {
  const matches = shiftType.match(/_(\d{1,2})_(\d{1,2})$/)
  if (!matches) {
    return { startHour: 10, endHour: 18 }
  }

  return {
    startHour: Number(matches[1]),
    endHour: Number(matches[2]),
  }
}

function formatHourInterval(shiftType: string): string {
  const { startHour, endHour } = parseShiftHours(shiftType)
  const start = `${startHour.toString().padStart(2, '0')}:00`
  const end = `${endHour.toString().padStart(2, '0')}:00`
  return `${start}-${end}`
}

function formatDateLabel(value: ShiftDateValue): string {
  const date = toLocalDate(value)
  return Number.isNaN(date.getTime())
    ? ''
    : new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }).format(date)
}

function isFutureShift(shift: BackendShift): boolean {
  const date = toLocalDate(shift.shiftDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date > today
}

function MyShiftsPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [shifts, setShifts] = useState<BackendShift[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const yearLabel = useMemo(() => new Date().getFullYear(), [])

  // Absence modal state
  const [absenceTarget, setAbsenceTarget] = useState<BackendShift | null>(null)
  const [absenceReason, setAbsenceReason] = useState('')
  const [absenceSubmitting, setAbsenceSubmitting] = useState(false)
  const [absenceResult, setAbsenceResult] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  // Track which shifts are already reported
  const [reportedShiftIds, setReportedShiftIds] = useState<Set<number>>(new Set())

  useEffect(() => {
    const load = async () => {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const results = await getMyShifts()
        setShifts(results)
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 401) {
          logout()
          navigate('/login', { replace: true })
          return
        }
        if (axios.isAxiosError(error) && error.response?.status === 403) {
          setErrorMessage('Only employees can view this page.')
          return
        }
        setErrorMessage('Could not load your shifts right now.')
      } finally {
        setIsLoading(false)
      }
    }

    void load()
  }, [logout, navigate])

  const openAbsenceModal = (shift: BackendShift) => {
    setAbsenceTarget(shift)
    setAbsenceReason('')
    setAbsenceResult(null)
  }

  const closeAbsenceModal = () => {
    setAbsenceTarget(null)
    setAbsenceReason('')
    setAbsenceResult(null)
  }

  const submitAbsence = async () => {
    if (!absenceTarget) return
    setAbsenceSubmitting(true)
    setAbsenceResult(null)

    try {
      await reportAbsence(absenceTarget.id, absenceReason || undefined)
      setReportedShiftIds((prev) => new Set(prev).add(absenceTarget.id))
      setAbsenceResult({ type: 'success', text: 'Your manager has been notified. A replacement will be found.' })
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not report absence. Please try again.'
      setAbsenceResult({ type: 'error', text: msg })
    } finally {
      setAbsenceSubmitting(false)
    }
  }

  function getStatusBadge(shift: BackendShift) {
    if (shift.status === 'ABSENT') return <span className="shift-badge absent">Absent</span>
    if (shift.status === 'REPLACEMENT') return <span className="shift-badge replacement">Replacement</span>
    return null
  }

  return (
    <main className="my-shifts-shell">
      <section className="my-shifts-card">
        <header className="my-shifts-header">
          <div>
            <p className="my-shifts-brand">QuickShift</p>
            <h1>{yearLabel} Your Shift Calendar</h1>
            <p className="my-shifts-subtitle">
              {currentUser?.storeName ? `Store: ${currentUser.storeName}` : 'Your upcoming shifts.'}
            </p>
          </div>
          <div className="my-shifts-actions">
            <button type="button" className="my-shifts-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button type="button" className="my-shifts-secondary" onClick={() => navigate('/notifications')}>
              Notifications
            </button>
            <button
              type="button"
              className="my-shifts-secondary"
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
          <p className="my-shifts-status error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className="my-shifts-table" aria-busy={isLoading}>
          {isLoading ? (
            <p className="my-shifts-empty">Loading shifts...</p>
          ) : shifts.length === 0 ? (
            <p className="my-shifts-empty">No shifts assigned yet.</p>
          ) : (
            <div className="my-shifts-grid">
              <div className="my-shifts-row header">
                <span>Date</span>
                <span>Shift</span>
                <span>Type</span>
                <span>Status</span>
                <span>Action</span>
              </div>
              {shifts.map((shift) => {
                const canReport =
                  isFutureShift(shift) &&
                  (shift.status === 'SCHEDULED' || shift.status === 'REPLACEMENT') &&
                  !reportedShiftIds.has(shift.id)

                return (
                  <div className="my-shifts-row" key={shift.id}>
                    <span>{formatDateLabel(shift.shiftDate)}</span>
                    <span>{formatHourInterval(shift.shiftType)}</span>
                    <span>{shift.shiftType.startsWith('PART_TIME') ? 'Part time' : 'Full time'}</span>
                    <span>{getStatusBadge(shift) ?? <span className="shift-badge scheduled">Scheduled</span>}</span>
                    <span>
                      {canReport ? (
                        <button
                          type="button"
                          className="report-absence-btn"
                          onClick={() => openAbsenceModal(shift)}
                        >
                          Report Absence
                        </button>
                      ) : reportedShiftIds.has(shift.id) ? (
                        <span className="shift-reported">Reported ✓</span>
                      ) : null}
                    </span>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </section>

      {/* Absence Report Modal */}
      {absenceTarget ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="absence-modal-title">
          <div className="modal-card">
            <h2 id="absence-modal-title">Report Absence</h2>
            <p className="modal-shift-info">
              <strong>Shift:</strong> {formatDateLabel(absenceTarget.shiftDate)} —{' '}
              {formatHourInterval(absenceTarget.shiftType)}
            </p>
            <p className="modal-desc">
              Your manager will be notified and a replacement will be found. This cannot be undone.
            </p>
            <label htmlFor="absence-reason" className="modal-label">
              Reason <span className="modal-optional">(optional)</span>
            </label>
            <textarea
              id="absence-reason"
              className="modal-textarea"
              rows={3}
              placeholder="e.g. Family emergency"
              value={absenceReason}
              onChange={(e) => setAbsenceReason(e.target.value)}
              disabled={absenceSubmitting}
            />
            {absenceResult ? (
              <p className={`modal-result ${absenceResult.type}`} role="alert">
                {absenceResult.text}
              </p>
            ) : null}
            <div className="modal-actions">
              <button
                type="button"
                className="my-shifts-secondary"
                onClick={closeAbsenceModal}
                disabled={absenceSubmitting}
              >
                Cancel
              </button>
              {!absenceResult || absenceResult.type === 'error' ? (
                <button
                  type="button"
                  className="report-absence-btn"
                  onClick={submitAbsence}
                  disabled={absenceSubmitting}
                >
                  {absenceSubmitting ? 'Sending...' : 'Confirm absence'}
                </button>
              ) : (
                <button type="button" className="my-shifts-secondary" onClick={closeAbsenceModal}>
                  Close
                </button>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </main>
  )
}

export default MyShiftsPage
