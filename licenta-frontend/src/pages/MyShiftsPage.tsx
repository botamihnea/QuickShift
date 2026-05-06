import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getMyShifts } from '../api/shiftService'
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

function MyShiftsPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [shifts, setShifts] = useState<BackendShift[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const yearLabel = useMemo(() => new Date().getFullYear(), [])

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
              </div>
              {shifts.map((shift) => (
                <div className="my-shifts-row" key={shift.id}>
                  <span>{formatDateLabel(shift.shiftDate)}</span>
                  <span>{formatHourInterval(shift.shiftType)}</span>
                  <span>{shift.shiftType.startsWith('PART_TIME') ? 'Part time' : 'Full time'}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </main>
  )
}

export default MyShiftsPage
