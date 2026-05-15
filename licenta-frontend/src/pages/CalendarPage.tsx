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
import { generateNextMonthShifts, generateScheduleForMonth, getAllShifts, acknowledgeAbsence } from '../api/shiftService'
import { approveLeaveRequest, denyLeaveRequest } from '../api/leaveService'
import { getNotifications, markNotificationRead } from '../api/notificationService'
import { getStores, updateMyStoreThreshold } from '../api/storeService'
import { useAuth } from '../auth/useAuth'
import type {
  AcknowledgeAbsenceResponse,
  BackendShift,
  GenerateScheduleResponse,
  LeaveRequestResponse,
  NotificationItem,
  ShiftCalendarEvent,
  StoreSummary,
} from '../types'
import './CalendarPage.css'

const localizer = momentLocalizer(moment)

function formatMonthYear(date: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
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

function getWelcomeName(email?: string | null, role?: string | null): string {
  if (role === 'ADMIN') {
    return 'Admin'
  }
  if (role === 'MANAGER' && email) {
    return email.split('@')[0] || 'Manager'
  }
  return 'there'
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
  const events: ShiftCalendarEvent[] = []

  for (const shift of shifts) {
    const baseDate = toLocalDate(shift.shiftDate)
    const { startHour, endHour } = parseShiftHours(shift.shiftType)

    if (Number.isNaN(baseDate.getTime())) {
      continue
    }

    const start = new Date(baseDate)
    start.setHours(startHour, 0, 0, 0)

    const end = new Date(baseDate)
    end.setHours(endHour, 0, 0, 0)

    const status = shift.status ?? 'SCHEDULED'
    const isReplacement = status === 'REPLACEMENT'
    const employeeLabel = status === 'ABSENT'
      ? `[ABSENT] ${shift.employee.fullName}`
      : isReplacement
        ? `[+] ${shift.employee.fullName}`
        : shift.employee.fullName

    events.push({
      title: buildMonthLabel(shift.shiftType, employeeLabel),
      start,
      end,
      allDay: false,
      resource: {
        isPartTime: shift.shiftType.startsWith('PART_TIME'),
        rawShiftType: shift.shiftType,
        employeeName: shift.employee.fullName,
        // Always show full name — no compact initials anywhere on the calendar
        compactEmployeeName: employeeLabel,
        timeRange: formatHourInterval(shift.shiftType),
        status,
      },
    })
  }

  return events
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
  const welcomeName = getWelcomeName(currentUser?.email, currentUser?.role)
  const [isGenerating, setIsGenerating] = useState(false)
  const [isLoadingShifts, setIsLoadingShifts] = useState(true)
  const [isLoadingNotifications, setIsLoadingNotifications] = useState(false)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const [stores, setStores] = useState<StoreSummary[]>([])
  const [isLoadingStores, setIsLoadingStores] = useState(false)
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null)
  // Per-notification acknowledge state for the inline CalendarPage panel
  const [calendarAckState, setCalendarAckState] = useState<
    Record<number, { pending: boolean; result: AcknowledgeAbsenceResponse | null; error: string | null }>
  >({})
  const [calendarLeaveState, setCalendarLeaveState] = useState<
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
  const [thresholdInput, setThresholdInput] = useState('')
  const [thresholdStatus, setThresholdStatus] = useState<string | null>(null)
  const [isThresholdOpen, setIsThresholdOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [response, setResponse] = useState<GenerateScheduleResponse | null>(null)
  const [shiftEvents, setShiftEvents] = useState<ShiftCalendarEvent[]>([])
  const [calendarDate, setCalendarDate] = useState<Date>(getNextMonthDate())
  const [calendarView, setCalendarView] = useState<View>(Views.MONTH)
  const [language, setLanguage] = useState<'en' | 'ro'>('en')

  const isRomanian = language === 'ro'
  const locale = isRomanian ? 'ro-RO' : 'en-US'
  const uiText = useMemo(
    () =>
      isRomanian
        ? {
            shiftCalendarTitle: 'Calendar ture',
            storeView: 'Vizualizare magazin',
            noStores: 'Nu exista magazine',
            manageStores: 'Gestionare magazine',
            employees: 'Angajati',
            manageMyShifts: 'Gestionare ture',
            notifications: 'Notificari',
            updateThreshold: 'Actualizeaza prag',
            generating: 'Se genereaza...',
            generateNextMonth: 'Genereaza',
            generateForMonth: 'Genereaza pentru luna',
            changePassword: 'Schimba parola',
            logOut: 'Deconectare',
            newNotifications: 'Notificari noi',
            refreshing: 'Se actualizeaza...',
            refresh: 'Reincarca',
            noNotifications: 'Nu exista notificari.',
            replacementAssigned: 'Inlocuitor atribuit',
            noReplacement: 'Nu s-a gasit inlocuitor — este necesara interventia manuala.',
            processing: 'Se proceseaza...',
            acknowledgeReplace: 'Confirmare si inlocuire',
            approveLeave: 'Aproba concediu',
            denyLeave: 'Respinge',
            denialReason: 'Motiv respingere',
            confirmDeny: 'Confirma respingerea',
            cancelDeny: 'Renunta',
            leaveApproved: 'Concediu aprobat',
            leaveDenied: 'Concediu respins',
            regenerateMonth: 'Regenereaza luna',
            regenerating: 'Se regenereaza...',
            regenerateSuccess: 'Ture regenerate pentru luna respectiva.',
            regenerateError: 'Nu se pot regenera turele acum.',
            markRead: 'Marcheaza ca citit',
            validationNote:
              'Vizualizare principala pentru validare: ture generate, nume angajati, si eticheta PT clara pentru part-time.',
            shiftsGeneratedSuffix: 'ture generate.',
            loadingShifts: 'Se incarca turele...',
            updateBusyDayTitle: 'Actualizeaza pragul pentru zile aglomerate',
            updateBusyDayBody:
              'Seteaza valoarea vanzarilor care declanseaza personal suplimentar pentru magazin.',
            thresholdPlaceholder: 'ex: 2000',
            cancel: 'Renunta',
            save: 'Salveaza',
            positiveNumber: 'Introdu un numar pozitiv.',
            thresholdSuccess: 'Pragul a fost actualizat.',
            thresholdError: 'Nu se poate actualiza pragul acum.',
            generateError:
              'Nu se pot genera turele acum. Verifica daca backend-ul ruleaza si incearca din nou.',
            generateForMonthError: 'Nu se pot genera turele pentru luna selectata. Incearca din nou.',
            loadShiftsError: 'Nu se pot incarca turele. Verifica daca backend-ul ruleaza.',
            translateToRomanian: 'Tradu in romana',
            translateToEnglish: 'Tradu in engleza',
            storeViewLabel: 'Vizualizare magazin',
          }
        : {
            shiftCalendarTitle: 'Shift Calendar',
            storeView: 'Store view',
            noStores: 'No stores available',
            manageStores: 'Manage stores',
            employees: 'Employees',
            manageMyShifts: 'Manage my shifts',
            notifications: 'Notifications',
            updateThreshold: 'Update threshold',
            generating: 'Generating...',
            generateNextMonth: 'Generate',
            generateForMonth: 'Generate for month',
            changePassword: 'Change password',
            logOut: 'Log out',
            newNotifications: 'New notifications',
            refreshing: 'Refreshing...',
            refresh: 'Refresh',
            noNotifications: 'No notifications yet.',
            replacementAssigned: 'Replacement assigned',
            noReplacement: 'No replacement found — manual action required.',
            processing: 'Processing...',
            acknowledgeReplace: 'Acknowledge & Replace',
            approveLeave: 'Approve leave',
            denyLeave: 'Deny',
            denialReason: 'Denial reason',
            confirmDeny: 'Confirm deny',
            cancelDeny: 'Cancel',
            leaveApproved: 'Leave approved',
            leaveDenied: 'Leave denied',
            regenerateMonth: 'Regenerate month',
            regenerating: 'Regenerating...',
            regenerateSuccess: 'Shifts regenerated for that month.',
            regenerateError: 'Could not regenerate shifts right now.',
            markRead: 'Mark read',
            validationNote:
              'Main view for validation: generated shifts, employee names, and clear PT label for part-time entries.',
            shiftsGeneratedSuffix: 'shifts generated.',
            loadingShifts: 'Loading shifts...',
            updateBusyDayTitle: 'Update busy day threshold',
            updateBusyDayBody: 'Set the sales value that triggers extra staffing for your store.',
            thresholdPlaceholder: 'e.g. 2000',
            cancel: 'Cancel',
            save: 'Save',
            positiveNumber: 'Please enter a positive number.',
            thresholdSuccess: 'Threshold updated successfully.',
            thresholdError: 'Could not update threshold right now.',
            generateError:
              'Could not generate shifts right now. Please verify backend is running and try again.',
            generateForMonthError: 'Could not generate shifts for that month. Please try again.',
            loadShiftsError: 'Could not load shifts. Please verify backend is running.',
            translateToRomanian: 'Translate to Romanian',
            translateToEnglish: 'Translate to English',
            storeViewLabel: 'Store view',
          },
    [isRomanian],
  )

  const nextMonthLabel = useMemo(() => formatMonthYear(getNextMonthDate(), locale), [locale])
  const calendarYearLabel = useMemo(() => calendarDate.getFullYear(), [calendarDate])
  /*
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
        label: formatMonthYear(date, locale),
        year,
        month,
      })
    }
    return options
  }, [locale])
  const [selectedMonthValue, setSelectedMonthValue] = useState(monthOptions[0]?.value ?? '')
  */

  const resolveYearMonth = (dateValue: string): { year: number; month: number } | null => {
    const parsed = new Date(dateValue)
    if (Number.isNaN(parsed.getTime())) return null
    return { year: parsed.getFullYear(), month: parsed.getMonth() + 1 }
  }

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
        setErrorMessage(uiText.loadShiftsError)
      }
    } finally {
      setIsLoadingShifts(false)
    }
  }, [isAdmin, logout, navigate, selectedStoreId, uiText.loadShiftsError])

  useEffect(() => {
    void fetchShifts()
  }, [fetchShifts])

  const loadNotifications = useCallback(async () => {
    if (!currentUser) {
      return
    }

    setIsLoadingNotifications(true)
    try {
      const items = await getNotifications()
      setNotifications(items)
    } finally {
      setIsLoadingNotifications(false)
    }
  }, [currentUser])

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
        setErrorMessage(uiText.generateError)
      }
      setResponse(null)
    } finally {
      setIsGenerating(false)
    }
  }

  /*
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
        setErrorMessage(uiText.generateForMonthError)
      }
      setResponse(null)
    } finally {
      setIsGenerating(false)
    }
  }
  */

  const handleApproveLeave = async (notificationId: number, leaveRequestId: number) => {
    setCalendarLeaveState((prev) => ({
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
      setCalendarLeaveState((prev) => ({
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
          : uiText.generateError
      setCalendarLeaveState((prev) => ({
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
    setCalendarLeaveState((prev) => ({
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
      setCalendarLeaveState((prev) => ({
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
          : uiText.generateError
      setCalendarLeaveState((prev) => ({
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
      setCalendarLeaveState((prev) => ({
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
          regenerateResult: uiText.regenerateError,
        },
      }))
      return
    }

    setCalendarLeaveState((prev) => ({
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
      await generateScheduleForMonth(
        resolved.year,
        resolved.month,
        isAdmin ? selectedStoreId ?? undefined : undefined,
      )
      setCalendarLeaveState((prev) => ({
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
          regenerateResult: uiText.regenerateSuccess,
        },
      }))
      void fetchShifts()
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : uiText.regenerateError
      setCalendarLeaveState((prev) => ({
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

  const unreadNotifications = notifications.filter((item) => !item.read)
  const canGenerate = isAdmin || isManager
  const thresholdStatusTone = thresholdStatus?.toLowerCase().includes('success') ? 'success' : 'error'

  return (
    <main className="page-shell">
      <section className="top-bar">
        <div>
          <p className="brand">QuickShift</p>
          <p className="brand-subtitle">Welcome {welcomeName}</p>
          <h1>{calendarYearLabel} {uiText.shiftCalendarTitle}</h1>
          {isAdmin ? (
            <div className="store-filter">
              <label>
                {uiText.storeView}
                <select
                  value={selectedStoreId ?? ''}
                  onChange={(event) => {
                    const value = Number(event.target.value)
                    setSelectedStoreId(Number.isNaN(value) ? null : value)
                  }}
                  disabled={isLoadingStores}
                >
                  {stores.length === 0 ? (
                    <option value="">{uiText.noStores}</option>
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
              {uiText.manageStores}
            </button>
          ) : null}
          {isManager ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/employees')}
            >
              {uiText.employees}
            </button>
          ) : null}
          {currentUser?.role === 'EMPLOYEE' ? (
            <>
              <button
                type="button"
                className="admin-btn"
                onClick={() => navigate('/my-shifts')}
              >
                {uiText.manageMyShifts}
              </button>
              <button
                type="button"
                className="admin-btn"
                onClick={() => navigate('/notifications')}
              >
                {uiText.notifications}
              </button>
            </>
          ) : null}
          {isManager ? (
            <button
              type="button"
              className="admin-btn"
              onClick={() => navigate('/notifications')}
            >
              {uiText.notifications}
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
              {uiText.updateThreshold}
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
                {isGenerating ? uiText.generating : `${uiText.generateNextMonth} ${nextMonthLabel}`}
              </button>
              {/*
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
                  {uiText.generateForMonth}
                </button>
              </div>
              */}
            </>
          ) : null}
          <button
            type="button"
            className="admin-btn"
            onClick={() => setLanguage(isRomanian ? 'en' : 'ro')}
          >
            {isRomanian ? uiText.translateToEnglish : uiText.translateToRomanian}
          </button>
          <button
            type="button"
            className="admin-btn"
            onClick={() => navigate('/change-password')}
          >
            {uiText.changePassword}
          </button>
          <button
            type="button"
            className="logout-btn"
            onClick={() => {
              logout()
              navigate('/', { replace: true })
            }}
          >
            {uiText.logOut}
          </button>
        </div>
      </section>

      <section className="calendar-panel">
        <div className="calendar-meta">
          {currentUser?.storeName ? (
            <p className="meta">{uiText.storeViewLabel}: {currentUser.storeName}</p>
          ) : null}
          {currentUser != null ? (
            <div className="notification-panel">
              <div className="notification-header">
                <p className="notification-title">{uiText.newNotifications}</p>
                <button
                  type="button"
                  className="notification-refresh"
                  onClick={() => {
                    void loadNotifications()
                  }}
                  disabled={isLoadingNotifications}
                >
                  {isLoadingNotifications ? uiText.refreshing : uiText.refresh}
                </button>
              </div>
              {unreadNotifications.length === 0 ? (
                <p className="notification-empty">{uiText.noNotifications}</p>
              ) : (
                <ul className="notification-list">
                  {unreadNotifications.map((item) => {
                    const ackState = calendarAckState[item.id]
                    const isAbsenceNotification = item.relatedAbsenceRequestId != null
                    const isLeaveNotification = item.relatedLeaveRequestId != null
                    const leaveState = calendarLeaveState[item.id]

                    const handleAck = async () => {
                      if (!item.relatedAbsenceRequestId) return
                      setCalendarAckState((prev) => ({
                        ...prev,
                        [item.id]: { pending: true, result: null, error: null },
                      }))
                      try {
                        const result = await acknowledgeAbsence(item.relatedAbsenceRequestId)
                        setCalendarAckState((prev) => ({
                          ...prev,
                          [item.id]: { pending: false, result, error: null },
                        }))
                        // Mark as read and refresh calendar + notifications
                        await markNotificationRead(item.id)
                        void loadNotifications()
                        void fetchShifts()
                      } catch (err) {
                        const msg =
                          axios.isAxiosError(err) && typeof err.response?.data === 'string'
                            ? err.response.data
                            : 'Could not process. Please try again.'
                        setCalendarAckState((prev) => ({
                          ...prev,
                          [item.id]: { pending: false, result: null, error: msg },
                        }))
                      }
                    }

                    return (
                      <li key={item.id} className={item.read ? 'notification-item' : 'notification-item unread'}>
                        <div>
                          <p className="notification-message">{item.message}</p>
                          <p className="notification-meta">
                            {item.storeName ? `${item.storeName} · ` : ''}
                            {new Date(item.createdAt).toLocaleString()}
                          </p>
                          {ackState?.result ? (
                            <p className="notification-ack-result">
                              {ackState.result.replacementFound
                                ? `${uiText.replacementAssigned}: ${ackState.result.replacementEmployeeName}`
                                : uiText.noReplacement}
                            </p>
                          ) : null}
                          {ackState?.error ? (
                            <p className="notification-ack-result error">{ackState.error}</p>
                          ) : null}
                          {leaveState?.decision === 'approved' ? (
                            <p className="notification-ack-result">{uiText.leaveApproved}</p>
                          ) : null}
                          {leaveState?.decision === 'denied' ? (
                            <p className="notification-ack-result warning">{uiText.leaveDenied}</p>
                          ) : null}
                          {leaveState?.error ? (
                            <p className="notification-ack-result error">{leaveState.error}</p>
                          ) : null}
                          {leaveState?.regenerateResult ? (
                            <p className="notification-ack-result warning">{leaveState.regenerateResult}</p>
                          ) : null}
                        </div>
                        {leaveState?.decision === 'approved' ? (
                          <div className="notification-action-stack">
                            {leaveState.response ? (
                              <button
                                type="button"
                                className="notification-acknowledge"
                                disabled={leaveState.regeneratePending}
                                onClick={() => handleRegenerateForLeave(item.id, leaveState.response!)}
                              >
                                {leaveState.regeneratePending ? uiText.regenerating : uiText.regenerateMonth}
                              </button>
                            ) : null}
                          </div>
                        ) : leaveState?.decision === 'denied' ? null : isLeaveNotification ? (
                          leaveState?.denyOpen ? (
                            <div className="notification-deny-panel">
                              <label className="notification-deny-label" htmlFor={`calendar-deny-${item.id}`}>
                                {uiText.denialReason}
                              </label>
                              <textarea
                                id={`calendar-deny-${item.id}`}
                                className="notification-deny-input"
                                rows={2}
                                value={leaveState?.reason ?? ''}
                                onChange={(event) =>
                                  setCalendarLeaveState((prev) => ({
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
                                disabled={leaveState?.pending}
                              />
                              <div className="notification-deny-actions">
                                <button
                                  type="button"
                                  className="notification-acknowledge"
                                  disabled={leaveState?.pending}
                                  onClick={() =>
                                    handleDenyLeave(item.id, item.relatedLeaveRequestId!, leaveState?.reason?.trim())
                                  }
                                >
                                  {leaveState?.pending ? uiText.processing : uiText.confirmDeny}
                                </button>
                                <button
                                  type="button"
                                  className="notification-mark"
                                  disabled={leaveState?.pending}
                                  onClick={() =>
                                    setCalendarLeaveState((prev) => ({
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
                                  {uiText.cancelDeny}
                                </button>
                              </div>
                            </div>
                          ) : (
                            <div className="notification-action-buttons">
                              <button
                                type="button"
                                className="notification-acknowledge"
                                disabled={leaveState?.pending}
                                onClick={() => handleApproveLeave(item.id, item.relatedLeaveRequestId!)}
                              >
                                {leaveState?.pending ? uiText.processing : uiText.approveLeave}
                              </button>
                              <button
                                type="button"
                                className="notification-mark"
                                disabled={leaveState?.pending}
                                onClick={() =>
                                  setCalendarLeaveState((prev) => ({
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
                                {uiText.denyLeave}
                              </button>
                            </div>
                          )
                        ) : isAbsenceNotification && !ackState?.result ? (
                          <button
                            type="button"
                            className="notification-acknowledge"
                            disabled={ackState?.pending}
                            onClick={handleAck}
                          >
                            {ackState?.pending ? uiText.processing : uiText.acknowledgeReplace}
                          </button>
                        ) : !isAbsenceNotification && !item.read ? (
                          <button
                            type="button"
                            className="notification-mark"
                            onClick={async () => {
                              await markNotificationRead(item.id)
                              void loadNotifications()
                            }}
                          >
                            {uiText.markRead}
                          </button>
                        ) : null}
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          ) : null}
          <p className="meta">
            {uiText.validationNote}
          </p>
          {response ? (
            <p className="status success" role="status">
              {response.message} {response.generatedShifts} {uiText.shiftsGeneratedSuffix}
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
            <p className="loading">{uiText.loadingShifts}</p>
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
                if (event.resource.status === 'ABSENT') {
                  return { className: 'event-absent' }
                }
                if (event.resource.status === 'REPLACEMENT') {
                  return { className: 'event-replacement' }
                }
                if (event.resource.isPartTime) {
                  return { className: 'event-part-time' }
                }
                return { className: 'event-full-time' }
              }}
            />
          )}
        </div>
      </section>

      {isThresholdOpen ? (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-card">
            <h2>{uiText.updateBusyDayTitle}</h2>
            <p>{uiText.updateBusyDayBody}</p>
            <input
              type="number"
              min={50}
              step={50}
              value={thresholdInput}
              onChange={(event) => setThresholdInput(event.target.value)}
              placeholder={uiText.thresholdPlaceholder}
            />
            {thresholdStatus ? <p className="modal-status">{thresholdStatus}</p> : null}
            <div className="modal-actions">
              <button
                type="button"
                className="admin-btn"
                onClick={() => setIsThresholdOpen(false)}
              >
                {uiText.cancel}
              </button>
              <button
                type="button"
                className="generate-btn"
                onClick={async () => {
                  const parsed = Number(thresholdInput)
                  if (!Number.isFinite(parsed) || parsed <= 0) {
                    setThresholdStatus(uiText.positiveNumber)
                    return
                  }

                  try {
                    await updateMyStoreThreshold(parsed)
                    setThresholdStatus(uiText.thresholdSuccess)
                    setIsThresholdOpen(false)
                  } catch {
                    setThresholdStatus(uiText.thresholdError)
                  }
                }}
              >
                {uiText.save}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  )
}

export default CalendarPage
