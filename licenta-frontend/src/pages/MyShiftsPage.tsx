import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMyEmployee } from '../api/employeeService'
import { requestLeave } from '../api/leaveService'
import { getMyShifts, reportAbsence } from '../api/shiftService'
import { useAuth } from '../auth/useAuth'
import type { BackendShift, EmployeeSelf } from '../types'
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

function getWelcomeName(fullName?: string | null, email?: string | null): string {
  if (fullName && fullName.trim().length > 0) {
    return fullName
  }
  if (email) {
    return email.split('@')[0] || 'there'
  }
  return 'there'
}

function isFutureShift(shift: BackendShift): boolean {
  const date = toLocalDate(shift.shiftDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date > today
}

function toInputDate(value: string): Date | null {
  if (!value) return null
  const parsed = new Date(value)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

function calculateInclusiveDays(start: string, end: string): number {
  const startDate = toInputDate(start)
  const endDate = toInputDate(end)
  if (!startDate || !endDate) return 0
  const msPerDay = 1000 * 60 * 60 * 24
  const diff = Math.floor((endDate.getTime() - startDate.getTime()) / msPerDay)
  return diff >= 0 ? diff + 1 : 0
}

function calculateDeductibleDays(calendarDays: number): number {
  const coefficient = 5 / 7
  return Math.ceil(calendarDays * coefficient)
}

function toInputValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getLeaveWindow(reference = new Date()): { start: Date; end: Date } {
  const start = new Date(reference.getFullYear(), reference.getMonth() + 1, 1)
  const end = new Date(reference.getFullYear(), reference.getMonth() + 2, 0)
  return { start, end }
}

function isWithinLeaveWindow(startValue: string, endValue: string, windowStart: Date, windowEnd: Date): boolean {
  const start = toInputDate(startValue)
  const end = toInputDate(endValue)
  if (!start || !end) return false
  return start >= windowStart && end <= windowEnd
}

function MyShiftsPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [employeeProfile, setEmployeeProfile] = useState<EmployeeSelf | null>(null)
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

  // Leave request state
  const [leaveOpen, setLeaveOpen] = useState(false)
  const [leaveStart, setLeaveStart] = useState('')
  const [leaveEnd, setLeaveEnd] = useState('')
  const [leaveReason, setLeaveReason] = useState('')
  const [leaveSubmitting, setLeaveSubmitting] = useState(false)
  const [leaveResult, setLeaveResult] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const leaveWindow = useMemo(() => getLeaveWindow(), [])
  const leaveWindowStart = useMemo(() => toInputValue(leaveWindow.start), [leaveWindow])
  const leaveWindowEnd = useMemo(() => toInputValue(leaveWindow.end), [leaveWindow])

  useEffect(() => {
    const load = async () => {
      setIsLoading(true)
      setErrorMessage(null)

      try {
        const [results, profile] = await Promise.all([getMyShifts(), getMyEmployee()])
        setShifts(results)
        setEmployeeProfile(profile)
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

  const openLeaveModal = () => {
    setLeaveOpen(true)
    setLeaveStart(leaveWindowStart)
    setLeaveEnd(leaveWindowStart)
    setLeaveReason('')
    setLeaveResult(null)
  }

  const closeLeaveModal = () => {
    setLeaveOpen(false)
    setLeaveStart('')
    setLeaveEnd('')
    setLeaveReason('')
    setLeaveResult(null)
  }

  const submitLeaveRequest = async () => {
    if (!leaveStart || !leaveEnd) return
    setLeaveSubmitting(true)
    setLeaveResult(null)

    try {
      await requestLeave({
        startDate: leaveStart,
        endDate: leaveEnd,
        reason: leaveReason || undefined,
      })
      setLeaveResult({
        type: 'success',
        text: 'Leave request submitted. Your manager will review it shortly.',
      })
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not submit leave request. Please try again.'
      setLeaveResult({ type: 'error', text: msg })
    } finally {
      setLeaveSubmitting(false)
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
            <p className="my-shifts-welcome">Welcome {getWelcomeName(employeeProfile?.fullName, currentUser?.email)}</p>
            <h1>{yearLabel} Your Shift Calendar</h1>
            <p className="my-shifts-subtitle">
              {currentUser?.storeName ? `Store: ${currentUser.storeName}` : 'Your upcoming shifts.'}
            </p>
            {employeeProfile ? (
              <p className="my-shifts-subtitle">
                Leave balance: {employeeProfile.remainingLeaveDays ?? 0} days
              </p>
            ) : null}
          </div>
          <div className="my-shifts-actions">
            <button type="button" className="my-shifts-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button type="button" className="my-shifts-secondary" onClick={openLeaveModal}>
              Request Leave
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

      {/* Leave Request Modal */}
      {leaveOpen ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="leave-modal-title">
          <div className="modal-card">
            <h2 id="leave-modal-title">Request Leave</h2>
            <p className="modal-desc">
              Requests are allowed only for {leaveWindow.start.toLocaleString('en-GB', { month: 'long', year: 'numeric' })}.
            </p>
            <div className="leave-grid">
              <label className="modal-label" htmlFor="leave-start">
                Start date
              </label>
              <input
                id="leave-start"
                className="modal-input"
                type="date"
                value={leaveStart}
                onChange={(event) => setLeaveStart(event.target.value)}
                min={leaveWindowStart}
                max={leaveWindowEnd}
                disabled={leaveSubmitting}
              />
              <label className="modal-label" htmlFor="leave-end">
                End date
              </label>
              <input
                id="leave-end"
                className="modal-input"
                type="date"
                value={leaveEnd}
                onChange={(event) => setLeaveEnd(event.target.value)}
                min={leaveWindowStart}
                max={leaveWindowEnd}
                disabled={leaveSubmitting}
              />
            </div>
            <label htmlFor="leave-reason" className="modal-label">
              Reason <span className="modal-optional">(optional)</span>
            </label>
            <textarea
              id="leave-reason"
              className="modal-textarea"
              rows={3}
              placeholder="e.g. Planned vacation"
              value={leaveReason}
              onChange={(event) => setLeaveReason(event.target.value)}
              disabled={leaveSubmitting}
            />
            {employeeProfile ? (
              <p className="leave-summary">
                Requested: {calculateInclusiveDays(leaveStart, leaveEnd)} days · Deductible: {calculateDeductibleDays(calculateInclusiveDays(leaveStart, leaveEnd))} · Remaining: {employeeProfile.remainingLeaveDays ?? 0}
              </p>
            ) : null}
            {employeeProfile &&
            calculateDeductibleDays(calculateInclusiveDays(leaveStart, leaveEnd)) > (employeeProfile.remainingLeaveDays ?? 0) ? (
              <p className="modal-result error" role="alert">
                Requested days exceed your remaining leave balance.
              </p>
            ) : null}
            {leaveStart && leaveEnd && !isWithinLeaveWindow(leaveStart, leaveEnd, leaveWindow.start, leaveWindow.end) ? (
              <p className="modal-result error" role="alert">
                Leave requests are only allowed for the next month.
              </p>
            ) : null}
            {employeeProfile && (employeeProfile.remainingLeaveDays ?? 0) <= 0 ? (
              <p className="modal-result error" role="alert">
                No remaining leave days. Please request directly from your manager.
              </p>
            ) : null}
            {leaveResult ? (
              <p className={`modal-result ${leaveResult.type}`} role="alert">
                {leaveResult.text}
              </p>
            ) : null}
            <div className="modal-actions">
              <button type="button" className="my-shifts-secondary" onClick={closeLeaveModal} disabled={leaveSubmitting}>
                Close
              </button>
              <button
                type="button"
                className="report-absence-btn"
                onClick={submitLeaveRequest}
                disabled={
                  leaveSubmitting ||
                  !leaveStart ||
                  !leaveEnd ||
                  calculateInclusiveDays(leaveStart, leaveEnd) === 0 ||
                  (employeeProfile?.remainingLeaveDays ?? 0) <= 0 ||
                  calculateDeductibleDays(calculateInclusiveDays(leaveStart, leaveEnd)) > (employeeProfile?.remainingLeaveDays ?? 0) ||
                  !isWithinLeaveWindow(leaveStart, leaveEnd, leaveWindow.start, leaveWindow.end)
                }
              >
                {leaveSubmitting ? 'Sending...' : 'Submit request'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  )
}

export default MyShiftsPage
