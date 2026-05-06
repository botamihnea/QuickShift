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
import { generateNextMonthShifts, generateScheduleForMonth, getAllShifts } from '../api/shiftService'
import { getNotifications, markNotificationRead } from '../api/notificationService'
import { getStores, updateMyStoreThreshold } from '../api/storeService'
import { useAuth } from '../auth/useAuth'
import type { BackendShift, GenerateScheduleResponse, NotificationItem, ShiftCalendarEvent, StoreSummary } from '../types'
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
  return shifts
    .map((shift) => {
      const baseDate = toLocalDate(shift.shiftDate)
      const { startHour, endHour } = parseShiftHours(shift.shiftType)

      if (Number.isNaN(baseDate.getTime())) {
        return null
      }

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
    .filter((event): event is ShiftCalendarEvent => event !== null)
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

type AgendaEventRendererProps = {
  event: ShiftCalendarEvent
}

function AgendaEventRenderer({ event }: AgendaEventRendererProps) {
  return (
    <div className="agenda-event">
      <span className="agenda-name">{event.resource.employeeName}</span>
      <span className="agenda-meta">
        {event.resource.isPartTime ? 'PT ' : ''}
        {event.resource.timeRange}
      </span>
    </div>
  )
}

function CalendarPage() {
  const navigate = useNavigate()
  const { currentUser, isAdmin, logout } = useAuth()
  const isManager = currentUser?.role === 'MANAGER'
  const [isGenerating, setIsGenerating] = useState(false)
  const [isLoadingShifts, setIsLoadingShifts] = useState(true)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [stores, setStores] = useState<StoreSummary[]>([])
  const [isLoadingStores, setIsLoadingStores] = useState(false)
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null)
  const [thresholdInput, setThresholdInput] = useState('')
  const [thresholdStatus, setThresholdStatus] = useState<string | null>(null)
  const [isThresholdOpen, setIsThresholdOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [response, setResponse] = useState<GenerateScheduleResponse | null>(null)
  const [shiftEvents, setShiftEvents] = useState<ShiftCalendarEvent[]>([])
  const [calendarDate, setCalendarDate] = useState<Date>(getNextMonthDate())
  const [calendarView, setCalendarView] = useState<View>(Views.MONTH)

  const nextMonthLabel = useMemo(() => formatMonthYear(getNextMonthDate()), [])
  const calendarYearLabel = useMemo(() => calendarDate.getFullYear(), [calendarDate])
  const monthOptions = useMemo(() => {
    const options: { value: string; label: string; year: number; month: number }[] = []
    const base = new Date()
    for (let i = 0; i < 12; i += 1) {
      const date = new Date(base.getFullYear(), base.getMonth() + i, 1)
      const year = date.getFullYear()
      const month = date.getMonth() + 1
      const value = `${year}-${String(month).padStart(2, '0')}`
      options.push({
        value,
        label: formatMonthYear(date),
        year,
        month,
      })
    }
    return options
  }, [])
  const [selectedMonthValue, setSelectedMonthValue] = useState(monthOptions[0]?.value ?? '')

  const fetchShifts = useCallback(async (): Promise<void> => {
    setIsLoadingShifts(true)

    try {
      const shifts = await getAllShifts(isAdmin ? selectedStoreId ?? undefined : undefined)
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
  }, [isAdmin, logout, navigate, selectedStoreId])

  useEffect(() => {
    void fetchShifts()
  }, [fetchShifts])

  const loadNotifications = useCallback(async () => {
    if (!isManager) {
      return
    }

    setIsLoadingNotifications(true)
    try {
      const items = await getNotifications()
      setNotifications(items)
    } finally {
      setIsLoadingNotifications(false)
    }
  }, [isManager])

  useEffect(() => {
    void loadNotifications()
  }, [loadNotifications])

  useEffect(() => {
    if (!isAdmin && !isManager) {
      return
    }

    let isCancelled = false

    const loadStores = async () => {
      setIsLoadingStores(true)

      try {
        const items = await getStores()
        if (isCancelled) {
          return
        }

        setStores(items)

        if (isAdmin && items.length > 0 && selectedStoreId === null) {
          setSelectedStoreId(items[0].id)
        }

        if (isManager && currentUser?.storeId) {
          const currentStore = items.find((store) => store.id === currentUser.storeId)
          if (currentStore?.busyDaySalesThreshold != null) {
            setThresholdInput(String(currentStore.busyDaySalesThreshold))
          }
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
  }, [currentUser?.storeId, isAdmin, isManager, selectedStoreId])

  const handleGenerateShifts = async (): Promise<void> => {
    setIsGenerating(true)
    setErrorMessage(null)

    try {
      const data = await generateNextMonthShifts(isAdmin ? selectedStoreId ?? undefined : undefined)
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

  const handleGenerateForMonth = async () => {
    const picked = monthOptions.find((option) => option.value === selectedMonthValue)
    if (!picked) {
      return
    }

    setIsGenerating(true)
    setErrorMessage(null)

    try {
      const data = await generateScheduleForMonth(
        picked.year,
        picked.month,
        isAdmin ? selectedStoreId ?? undefined : undefined,
      )
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
        setErrorMessage('Could not generate shifts for that month. Please try again.')
      }
      setResponse(null)
    } finally {
      setIsGenerating(false)
    }
  }

  const unreadNotifications = notifications.filter((item) => !item.read)
  const canGenerate = isAdmin || isManager
  const thresholdStatusTone = thresholdStatus?.toLowerCase().includes('success') ? 'success' : 'error'

  return (
    <main className="page-shell">
      <section className="top-bar">
        <div>
          <p className="brand">QuickShift</p>
          <h1>{calendarYearLabel} Shift Calendar</h1>
          {isAdmin ? (
            <div className="store-filter">
              <label>
                Store view
                <select
                  value={selectedStoreId ?? ''}
                  onChange={(event) => {
                    const value = Number(event.target.value)
                    setSelectedStoreId(Number.isNaN(value) ? null : value)
                  }}
                  disabled={isLoadingStores}
                >
                  {stores.length === 0 ? (
                    <option value="">No stores available</option>
                  ) : (
                    stores.map((store) => (
                      <option key={store.id} value={store.id}>
                        {store.storeName}
                      </option>
                    ))
                  )}
                </select>
              </label>
            </div>
          ) : null}
        </div>
        <div className="top-bar-actions">
          {isAdmin ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/admin/stores')}
            >
              Manage stores
            </button>
          ) : null}
          {isManager ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/employees')}
            >
              Employees
            </button>
          ) : null}
          {currentUser?.role === 'EMPLOYEE' ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/my-shifts')}
            >
              View your shifts
            </button>
          ) : null}
          {isManager ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/notifications')}
            >
              Notifications
            </button>
          ) : null}
          {isManager ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => {
                setThresholdStatus(null)
                setIsThresholdOpen(true)
              }}
            >
              Update threshold
            </button>
          ) : null}
          {canGenerate ? (
            <>
              <button
                type="button"
                className="generate-btn"
                onClick={handleGenerateShifts}
                disabled={isGenerating || (isAdmin && !selectedStoreId)}
              >
                {isGenerating ? 'Generating...' : `Generate ${nextMonthLabel}`}
              </button>
              <div className="generate-specific">
                <select
                  value={selectedMonthValue}
                  onChange={(event) => setSelectedMonthValue(event.target.value)}
                >
                  {monthOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="admin-btn"
                  onClick={handleGenerateForMonth}
                  disabled={isGenerating || (isAdmin && !selectedStoreId)}
                >
                  Generate for month
                </button>
              </div>
            </>
          ) : null}
          <button
            type="button"
            className="logout-btn"
            onClick={() => {
              logout()
              navigate('/', { replace: true })
            }}
          >
            Log out
          </button>
        </div>
      </section>

      <section className="calendar-panel">
        <div className="calendar-meta">
          {currentUser?.storeName ? (
            <p className="meta">Store view: {currentUser.storeName}</p>
          ) : null}
          {isManager ? (
            <div className="notification-panel">
              <div className="notification-header">
                <p className="notification-title">New notifications</p>
                <button
                  type="button"
                  className="notification-refresh"
                  onClick={() => {
                    void loadNotifications()
                  }}
                  disabled={isLoadingNotifications}
                >
                  {isLoadingNotifications ? 'Refreshing...' : 'Refresh'}
                </button>
              </div>
              {unreadNotifications.length === 0 ? (
                <p className="notification-empty">No notifications yet.</p>
              ) : (
                <ul className="notification-list">
                  {unreadNotifications.map((item) => (
                    <li key={item.id} className={item.read ? 'notification-item' : 'notification-item unread'}>
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
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ) : null}
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
          {thresholdStatus ? (
            <p className={`status ${thresholdStatusTone}`} role="status">
              {thresholdStatus}
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
              titleAccessor={(event) => {
                if (calendarView === Views.AGENDA) {
                  return `${event.resource.employeeName} | ${event.resource.isPartTime ? 'PT ' : ''}${event.resource.timeRange}`
                }

                return typeof event.title === 'string' ? event.title : ''
              }}
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
                agenda: {
                  event: AgendaEventRenderer,
                },
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

      {isThresholdOpen ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-card">
            <h2>Update busy day threshold</h2>
            <p>Set the sales value that triggers extra staffing for your store.</p>
            <input
              type="number"
              min={50}
              step={50}
              value={thresholdInput}
              onChange={(event) => setThresholdInput(event.target.value)}
              placeholder="e.g. 2000"
            />
            {thresholdStatus ? <p className="modal-status">{thresholdStatus}</p> : null}
            <div className="modal-actions">
              <button
                type="button"
                className="admin-btn"
                onClick={() => setIsThresholdOpen(false)}
              >
                Cancel
              </button>
              <button
                type="button"
                className="generate-btn"
                onClick={async () => {
                  const parsed = Number(thresholdInput)
                  if (!Number.isFinite(parsed) || parsed <= 0) {
                    setThresholdStatus('Please enter a positive number.')
                    return
                  }

                  try {
                    await updateMyStoreThreshold(parsed)
                    setThresholdStatus('Threshold updated successfully.')
                    setIsThresholdOpen(false)
                  } catch {
                    setThresholdStatus('Could not update threshold right now.')
                  }
                }}
              >
                Save
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  )
}

export default CalendarPage
