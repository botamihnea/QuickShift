import httpClient from './httpClient'
import type { BackendShift, GenerateScheduleResponse } from '../types'

export async function generateNextMonthShifts(storeId?: number): Promise<GenerateScheduleResponse> {
  const payload = storeId ? { storeId } : undefined
  const { data } = await httpClient.post<GenerateScheduleResponse>('/api/shifts/generate', payload)
  return data
}

export async function generateScheduleForMonth(
  year: number,
  month: number,
  storeId?: number,
): Promise<GenerateScheduleResponse> {
  const payload = {
    year,
    month,
    storeId,
  }
  const { data } = await httpClient.post<GenerateScheduleResponse>('/api/shifts/generate', payload)
  return data
}

export async function getAllShifts(storeId?: number): Promise<BackendShift[]> {
  const result = await httpClient.get<BackendShift[] | undefined>('/api/shifts', {
    params: storeId ? { storeId } : undefined,
  })
  return Array.isArray(result.data) ? result.data : []
}

export async function getMyShifts(): Promise<BackendShift[]> {
  const result = await httpClient.get<BackendShift[] | undefined>('/api/shifts/mine')
  return Array.isArray(result.data) ? result.data : []
}
