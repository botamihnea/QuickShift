import httpClient from './httpClient'
import type { BackendShift, GenerateScheduleResponse } from '../types'

export async function generateNextMonthShifts(): Promise<GenerateScheduleResponse> {
  const { data } = await httpClient.post<GenerateScheduleResponse>('/api/shifts/generate')
  return data
}

export async function getAllShifts(): Promise<BackendShift[]> {
  const result = await httpClient.get<BackendShift[] | undefined>('/api/shifts')
  return Array.isArray(result.data) ? result.data : []
}
