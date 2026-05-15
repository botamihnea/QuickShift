import axios from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteEmployee, getEmployees } from '../api/employeeService'
import { useAuth } from '../auth/useAuth'
import type { EmployeeSummary } from '../types'
import './EmployeesPage.css'

function formatLeaveDays(value: number | null): string {
  const safeValue = value ?? 0
  return safeValue.toFixed(2)
}

function buildMonthOptions(): Array<{ value: string; label: string }> {
  const options: Array<{ value: string; label: string }> = []
  const base = new Date()
  for (let i = 1; i <= 12; i += 1) {
    const date = new Date(base.getFullYear(), base.getMonth() + i, 1)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const value = `${year}-${month}`
    const label = new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' }).format(date)
    options.push({ value, label })
  }
  return options
}

function EmployeesPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [employees, setEmployees] = useState<EmployeeSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const monthOptions = useMemo(() => buildMonthOptions(), [])
  const [selectedMonthValue, setSelectedMonthValue] = useState(monthOptions[0]?.value ?? '')
  const selectedMonthLabel = useMemo(
    () => monthOptions.find((option) => option.value === selectedMonthValue)?.label ?? '',
    [monthOptions, selectedMonthValue],
  )

  const resolveYearMonth = (value: string): { year: number; month: number } | null => {
    if (!value) return null
    const [yearText, monthText] = value.split('-')
    const year = Number(yearText)
    const month = Number(monthText)
    if (!year || !month) return null
    return { year, month }
  }

  const loadEmployees = async () => {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const resolved = resolveYearMonth(selectedMonthValue)
      const results = await getEmployees(undefined, resolved?.year, resolved?.month)
      setEmployees(results)
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 401) {
        logout()
        navigate('/login', { replace: true })
        return
      }
      setErrorMessage('Could not load employees right now. Please try again.')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    void loadEmployees()
  }, [selectedMonthValue])

  const handleDelete = async (employeeId: number) => {
    setErrorMessage(null)

    try {
      await deleteEmployee(employeeId)
      await loadEmployees()
    } catch {
      setErrorMessage('Could not delete employee right now. Please try again.')
    }
  }

  return (
    <main className="employees-shell">
      <section className="employees-card">
        <header className="employees-header">
          <div>
            <p className="employees-brand">QuickShift</p>
            <h1>Store employees</h1>
            <p className="employees-subtitle">
              {currentUser?.storeName ? `Store: ${currentUser.storeName}` : 'Manage employees assigned to your store.'}
            </p>
            <p className="employees-subtitle">Planned hours for {selectedMonthLabel}</p>
          </div>
          <div className="employees-actions">
            <label className="employees-filter">
              Month
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
            </label>
            <button type="button" className="employees-secondary" onClick={() => navigate('/schedule')}>
              Back to schedule
            </button>
            <button
              type="button"
              className="employees-secondary"
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
          <p className="employees-status error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <div className="employees-list">
          {isLoading ? (
            <p className="employees-empty">Loading employees...</p>
          ) : employees.length === 0 ? (
            <p className="employees-empty">No employees found for your store.</p>
          ) : (
            employees.map((employee) => (
              <article className="employee-card" key={employee.id}>
                <div>
                  <p className="employee-name">{employee.fullName}</p>
                  <p className="employee-meta">
                    {employee.contractType.replace('_', ' ')} · {employee.shiftPreference}
                  </p>
                  <p className="employee-meta">
                    Leave days: {formatLeaveDays(employee.remainingLeaveDays)} · Recovery hours: {employee.holidayRecoveryHours ?? 0}
                  </p>
                  <p className="employee-meta">
                    Planned hours: {employee.plannedHours ?? 0}
                  </p>
                </div>
                <button
                  type="button"
                  className="employee-delete"
                  onClick={() => handleDelete(employee.id)}
                >
                  Remove
                </button>
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  )
}

export default EmployeesPage
