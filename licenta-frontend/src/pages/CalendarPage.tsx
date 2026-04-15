import axios from 'axios'
import moment from 'moment'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Calendar,
  momentLocalizer,
  type EventProps,
  type View,
  Views,
} from 'react-big-calendar'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import { generateNextMonthShifts, getAllShifts } from '../api/shiftService'
import { useAuth } from '../auth/useAuth'
import type { BackendShift, GenerateScheduleResponse, ShiftCalendarEvent } from '../types'
import './CalendarPage.css'

const localizer = momentLocalizer(moment)

function formatMonthYear(date: Date): string {
  return new Intl.DateTimeFormat('en-US', {
    month: 'long',
    year: 'numeric',
  }).format(date)
}

function getNextMonthDate(reference = new Date()): Date {
  return new Date(reference.getFullYear(), reference.getMonth() + 1, 1)
}

function toLocalDate(dateString: string): Date {
  const [year, month, day] = dateString.split('-').map(Number)
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

function toCompactEmployeeName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  if (parts.length <= 1) {
    return fullName
  }

  const lastInitial = parts[parts.length - 1][0]?.toUpperCase() ?? ''
  return `${parts[0]} ${lastInitial}.`
}

function buildMonthLabel(shiftType: string, employeeName: string): string {
  const timeRange = formatHourInterval(shiftType)
  const isPartTime = shiftType.startsWith('PART_TIME')

  if (isPartTime) {
    return `${employeeName} | PT ${timeRange}`
  }

  return `${employeeName} | ${timeRange}`
}

function toCalendarEvents(shifts: BackendShift[]): ShiftCalendarEvent[] {
  return shifts.map((shift) => {
    const baseDate = toLocalDate(shift.shiftDate)
    const { startHour, endHour } = parseShiftHours(shift.shiftType)

    const start = new Date(baseDate)
    start.setHours(startHour, 0, 0, 0)

    const end = new Date(baseDate)
    end.setHours(endHour, 0, 0, 0)

    return {
      title: buildMonthLabel(shift.shiftType, shift.employee.fullName),
      start,
      end,
      allDay: false,
      resource: {
        isPartTime: shift.shiftType.startsWith('PART_TIME'),
        rawShiftType: shift.shiftType,
        employeeName: shift.employee.fullName,
        compactEmployeeName: toCompactEmployeeName(shift.employee.fullName),
        timeRange: formatHourInterval(shift.shiftType),
      },
    }
  })
}

type ShiftEventRendererProps = EventProps<ShiftCalendarEvent>

function ShiftEventRenderer({ event }: ShiftEventRendererProps) {
  return (
    <div className="shift-event">
      <span className="shift-employee">{event.resource.compactEmployeeName}</span>
      <span className="shift-time">
        {event.resource.isPartTime ? 'PT ' : ''}
        {event.resource.timeRange}
      </span>
    </div>
  )
}

function CalendarPage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [isGenerating, setIsGenerating] = useState(false)
  const [isLoadingShifts, setIsLoadingShifts] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [response, setResponse] = useState<GenerateScheduleResponse | null>(null)
  const [shiftEvents, setShiftEvents] = useState<ShiftCalendarEvent[]>([])
  const [calendarDate, setCalendarDate] = useState<Date>(getNextMonthDate())
  const [calendarView, setCalendarView] = useState<View>(Views.MONTH)

  const nextMonthLabel = useMemo(() => formatMonthYear(getNextMonthDate()), [])

  const fetchShifts = useCallback(async (): Promise<void> => {
    setIsLoadingShifts(true)

    try {
      const shifts = await getAllShifts()
      setShiftEvents(toCalendarEvents(shifts))
      setErrorMessage(null)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
        return
      }

      if (axios.isAxiosError(error) && error.response?.status === 204) {
        setShiftEvents([])
        setErrorMessage(null)
      } else if (axios.isAxiosError(error) && typeof error.response?.data === 'string') {
        setErrorMessage(error.response.data)
      } else {
        setErrorMessage('Could not load shifts. Please verify backend is running.')
      }
    } finally {
      setIsLoadingShifts(false)
    }
  }, [logout, navigate])

  useEffect(() => {
    void fetchShifts()
  }, [fetchShifts])

  const handleGenerateShifts = async (): Promise<void> => {
    setIsGenerating(true)
    setErrorMessage(null)

    try {
      const data = await generateNextMonthShifts()
      setResponse(data)
      setCalendarDate(new Date(data.targetYear, data.targetMonth - 1, 1))
      setCalendarView(Views.MONTH)
      await fetchShifts()
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
        return
      }

      if (axios.isAxiosError(error) && typeof error.response?.data === 'string') {
        setErrorMessage(error.response.data)
      } else {
        setErrorMessage(
          'Could not generate shifts right now. Please verify backend is running and try again.',
        )
      }
      setResponse(null)
    } finally {
      setIsGenerating(false)
    }
  }

  return (
    <main className="page-shell">
      <section className="top-bar">
        <div>
          <p className="brand">QuickShift</p>
          <h1>{nextMonthLabel} Shift Calendar</h1>
        </div>
        <div className="top-bar-actions">
          <button
            type="button"
            className="generate-btn"
            onClick={handleGenerateShifts}
            disabled={isGenerating}
          >
            {isGenerating ? 'Generating...' : `Generate ${nextMonthLabel}`}
          </button>
          <button
            type="button"
            className="logout-btn"
            onClick={() => {
              logout()
              navigate('/login', { replace: true })
            }}
          >
            Log out
          </button>
        </div>
      </section>

      <section className="calendar-panel">
        <div className="calendar-meta">
          <p className="meta">
            Main view for validation: generated shifts, employee names, and clear
            `PT` label for part-time entries.
          </p>
          {response ? (
            <p className="status success" role="status">
              {response.message} {response.generatedShifts} shifts generated.
            </p>
          ) : null}
          {errorMessage ? (
            <p className="status error" role="alert">
              {errorMessage}
            </p>
          ) : null}
        </div>

        <div className="calendar-wrap" aria-busy={isLoadingShifts}>
          {isLoadingShifts ? (
            <p className="loading">Loading shifts...</p>
          ) : (
            <Calendar<ShiftCalendarEvent>
              localizer={localizer}
              events={shiftEvents}
              defaultView={Views.MONTH}
              date={calendarDate}
              view={calendarView}
              views={[Views.MONTH, Views.WEEK, Views.DAY, Views.AGENDA]}
              onNavigate={(date) => setCalendarDate(date)}
              onView={(view) => setCalendarView(view)}
              startAccessor="start"
              endAccessor="end"
              popup
              dayLayoutAlgorithm="no-overlap"
              className="quickshift-calendar"
              components={{
                event: ShiftEventRenderer,
              }}
              eventPropGetter={(event: ShiftCalendarEvent) => {
                if (event.resource.isPartTime) {
                  return {
                    className: 'event-part-time',
                  }
                }

                return {
                  className: 'event-full-time',
                }
              }}
            />
          )}
        </div>
      </section>
    </main>
  )
}

export default CalendarPage
