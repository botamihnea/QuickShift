import httpClient from './httpClient'
import type { AcknowledgeAbsenceResponse, BackendShift, GenerateScheduleResponse } from '../types'

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

export async function reportAbsence(shiftId: number, reason?: string): Promise<string> {
  const { data } = await httpClient.post<string>(`/api/shifts/${shiftId}/report-absence`, { reason })
  return data
}

export async function acknowledgeAbsence(absenceRequestId: number): Promise<AcknowledgeAbsenceResponse> {
  const { data } = await httpClient.post<AcknowledgeAbsenceResponse>(
    `/api/absence-requests/${absenceRequestId}/acknowledge`,
  )
  return data
}

