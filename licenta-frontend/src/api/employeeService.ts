import httpClient from './httpClient'
import type { EmployeeSummary } from '../types'

export async function getEmployees(storeId?: number): Promise<EmployeeSummary[]> {
  const query = typeof storeId === 'number' ? `?storeId=${storeId}` : ''
  const { data } = await httpClient.get<EmployeeSummary[]>(`/api/employees${query}`)
  return Array.isArray(data) ? data : []
}

export async function deleteEmployee(employeeId: number): Promise<void> {
  await httpClient.delete(`/api/employees/${employeeId}`)
}
