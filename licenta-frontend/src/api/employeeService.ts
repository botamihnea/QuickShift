import httpClient from './httpClient'
import type { EmployeeSelf, EmployeeSummary } from '../types'

export async function getEmployees(storeId?: number, year?: number, month?: number): Promise<EmployeeSummary[]> {
  const params = new URLSearchParams()
  if (typeof storeId === 'number') {
    params.set('storeId', String(storeId))
  }
  if (typeof year === 'number' && typeof month === 'number') {
    params.set('year', String(year))
    params.set('month', String(month))
  }
  const query = params.toString() ? `?${params.toString()}` : ''
  const { data } = await httpClient.get<EmployeeSummary[]>(`/api/employees${query}`)
  return Array.isArray(data) ? data : []
}

export async function deleteEmployee(employeeId: number): Promise<void> {
  await httpClient.delete(`/api/employees/${employeeId}`)
}

export async function getMyEmployee(): Promise<EmployeeSelf> {
  const { data } = await httpClient.get<EmployeeSelf>('/api/employees/me')
  return data
}
