import axios from 'axios'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { deleteEmployee, getEmployees } from '../api/employeeService'
import { useAuth } from '../auth/useAuth'
import type { EmployeeSummary } from '../types'
import './EmployeesPage.css'

function EmployeesPage() {
  const navigate = useNavigate()
  const { currentUser, logout } = useAuth()
  const [employees, setEmployees] = useState<EmployeeSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const loadEmployees = async () => {
    setIsLoading(true)
    setErrorMessage(null)

    try {
      const results = await getEmployees()
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
  }, [])

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
          </div>
          <div className="employees-actions">
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
                    Leave days: {employee.remainingLeaveDays ?? 0} · Recovery hours: {employee.holidayRecoveryHours ?? 0}
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
