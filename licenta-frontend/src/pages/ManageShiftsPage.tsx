import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import {
  createManualShift,
  deleteShift,
  getAllShifts,
  getEligibleEmployees,
} from '../api/shiftService'
import type { BackendShift, EligibleEmployee } from '../types'
import './ManageShiftsPage.css'

const SHIFT_OPTIONS = [
  { value: 'SHIFT_1_10_18', label: '8h (10:00-18:00)' },
  { value: 'SHIFT_2_14_22', label: '8h (14:00-22:00)' },
  { value: 'PART_TIME_10_16', label: '6h (10:00-16:00)' },
  { value: 'PART_TIME_16_22', label: '6h (16:00-22:00)' },
  { value: 'PART_TIME_10_14', label: '4h (10:00-14:00)' },
  { value: 'PART_TIME_16_20', label: '4h (16:00-20:00)' },
]

function formatDateLabel(value: string): string {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium' }).format(parsed)
}

function ManageShiftsPage() {
  const navigate = useNavigate()
  const { currentUser, logout, isAdmin } = useAuth()
  const [selectedDate, setSelectedDate] = useState('')
  const [shiftType, setShiftType] = useState(SHIFT_OPTIONS[0].value)
  const [eligibleEmployees, setEligibleEmployees] = useState<EligibleEmployee[]>([])
  const [selectedEmployeeId, setSelectedEmployeeId] = useState<number | ''>('')
  const [isLoadingEligible, setIsLoadingEligible] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [formSuccess, setFormSuccess] = useState<string | null>(null)
  const [shifts, setShifts] = useState<BackendShift[]>([])
  const [isLoadingShifts, setIsLoadingShifts] = useState(false)
  const selectedDateLabel = useMemo(() => (selectedDate ? formatDateLabel(selectedDate) : ''), [selectedDate])

  const loadEligible = async (date: string, type: string) => {
    setIsLoadingEligible(true)
    setFormError(null)

    try {
      const results = await getEligibleEmployees(date, type)
      setEligibleEmployees(results)
      if (results.length > 0) {
        setSelectedEmployeeId(results[0].id)
      } else {
        setSelectedEmployeeId('')
      }
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not load eligible employees.'
      setFormError(msg)
      setEligibleEmployees([])
      setSelectedEmployeeId('')
    } finally {
      setIsLoadingEligible(false)
    }
  }

  const loadShifts = async () => {
    setIsLoadingShifts(true)
    try {
      const results = await getAllShifts()
      setShifts(results)
    } catch {
      setShifts([])
    } finally {
      setIsLoadingShifts(false)
    }
  }

  useEffect(() => {
    void loadShifts()
  }, [])

  useEffect(() => {
    if (!selectedDate || !shiftType) {
      setEligibleEmployees([])
      setSelectedEmployeeId('')
      return
    }
    void loadEligible(selectedDate, shiftType)
  }, [selectedDate, shiftType])

  const filteredShifts = useMemo(() => {
    if (!selectedDate) return []
    return shifts.filter((shift) => {
      const date = typeof shift.shiftDate === 'string'
        ? shift.shiftDate
        : Array.isArray(shift.shiftDate)
          ? `${shift.shiftDate[0]}-${String(shift.shiftDate[1]).padStart(2, '0')}-${String(shift.shiftDate[2]).padStart(2, '0')}`
          : `${shift.shiftDate.year}-${String(shift.shiftDate.month).padStart(2, '0')}-${String(shift.shiftDate.day).padStart(2, '0')}`
      return date === selectedDate
    })
  }, [selectedDate, shifts])

  const handleAddShift = async () => {
    if (!selectedDate || !shiftType || !selectedEmployeeId) {
      setFormError('Date, shift type, and employee are required.')
      return
    }

    setIsSubmitting(true)
    setFormError(null)
    setFormSuccess(null)

    try {
      await createManualShift({
        date: selectedDate,
        shiftType,
        employeeId: Number(selectedEmployeeId),
        storeId: isAdmin ? currentUser?.storeId ?? undefined : undefined,
      })
      setFormSuccess('Shift added successfully.')
      await loadShifts()
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not add shift.'
      setFormError(msg)
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDeleteShift = async (shiftId: number) => {
    setFormError(null)
    setFormSuccess(null)

    try {
      await deleteShift(shiftId, isAdmin ? currentUser?.storeId ?? undefined : undefined)
      await loadShifts()
      setFormSuccess('Shift removed.')
    } catch (error) {
      const msg =
        axios.isAxiosError(error) && typeof error.response?.data === 'string'
          ? error.response.data
          : 'Could not remove shift.'
      setFormError(msg)
    }
  }

  return (
    <main className="manage-shifts-shell">
      <section className="manage-shifts-card">
        <header className="manage-shifts-header">
          <div>
            <p className="manage-shifts-brand">QuickShift</p>
            <h1>Manage shifts</h1>
            <p className="manage-shifts-subtitle">
              {currentUser?.storeName ? `Store: ${currentUser.storeName}` : 'Manual shift management.'}
            </p>
          </div>
          <div className="manage-shifts-actions">
            <button type="button" className="manage-shifts-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button
              type="button"
              className="manage-shifts-secondary"
              onClick={() => {
                logout()
                navigate('/', { replace: true })
              }}
            >
              Log out
            </button>
          </div>
        </header>

        <div className="manage-shifts-form">
          <div className="manage-shifts-field">
            <label htmlFor="shift-date">Date</label>
            <input
              id="shift-date"
              type="date"
              value={selectedDate}
              onChange={(event) => setSelectedDate(event.target.value)}
            />
          </div>
          <div className="manage-shifts-field">
            <label htmlFor="shift-type">Shift type</label>
            <select
              id="shift-type"
              value={shiftType}
              onChange={(event) => setShiftType(event.target.value)}
            >
              {SHIFT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div className="manage-shifts-field">
            <label htmlFor="shift-employee">Employee</label>
            <select
              id="shift-employee"
              value={selectedEmployeeId}
              onChange={(event) => setSelectedEmployeeId(Number(event.target.value))}
              disabled={isLoadingEligible || eligibleEmployees.length === 0}
            >
              {eligibleEmployees.length === 0 ? (
                <option value="">No eligible employees</option>
              ) : (
                eligibleEmployees.map((employee) => (
                  <option key={employee.id} value={employee.id}>
                    {employee.fullName} · {employee.contractType.replace('_', ' ')}
                  </option>
                ))
              )}
            </select>
          </div>
          <button
            type="button"
            className="manage-shifts-primary"
            onClick={handleAddShift}
            disabled={isSubmitting || !selectedDate || !selectedEmployeeId}
          >
            {isSubmitting ? 'Saving...' : 'Add shift'}
          </button>
        </div>

        {formError ? (
          <p className="manage-shifts-status error" role="alert">
            {formError}
          </p>
        ) : null}
        {formSuccess ? (
          <p className="manage-shifts-status success" role="status">
            {formSuccess}
          </p>
        ) : null}

        <div className="manage-shifts-list">
          <h2>Shifts for {selectedDateLabel || 'selected date'}</h2>
          {isLoadingShifts ? (
            <p className="manage-shifts-empty">Loading shifts...</p>
          ) : filteredShifts.length === 0 ? (
            <p className="manage-shifts-empty">No shifts found for this date.</p>
          ) : (
            <ul className="manage-shifts-items">
              {filteredShifts.map((shift) => (
                <li key={shift.id} className="manage-shifts-item">
                  <div>
                    <p className="manage-shifts-name">{shift.employee.fullName}</p>
                    <p className="manage-shifts-meta">
                      {shift.shiftType.replace(/_/g, ' ')} · {shift.status}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="manage-shifts-delete"
                    onClick={() => handleDeleteShift(shift.id)}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </main>
  )
}

export default ManageShiftsPage
