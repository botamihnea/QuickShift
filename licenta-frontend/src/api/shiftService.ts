import httpClient from './httpClient'
import type {
  AcknowledgeAbsenceResponse,
  BackendShift,
  EligibleEmployee,
  GenerateScheduleResponse,
  ManualShiftRequest,
} from '../types'

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

export async function approveReplacementOffer(offerId: number): Promise<string> {
  const { data } = await httpClient.post<string>(`/api/replacement-offers/${offerId}/approve`)
  return data
}

export async function denyReplacementOffer(offerId: number): Promise<string> {
  const { data } = await httpClient.post<string>(`/api/replacement-offers/${offerId}/deny`)
  return data
}

export async function findAnotherReplacement(absenceRequestId: number): Promise<string> {
  const { data } = await httpClient.post<string>(`/api/absence-requests/${absenceRequestId}/find-replacement`)
  return data
}

export async function getEligibleEmployees(
  date: string,
  shiftType: string,
  storeId?: number,
): Promise<EligibleEmployee[]> {
  const params: Record<string, string | number> = { date, shiftType }
  if (typeof storeId === 'number') {
    params.storeId = storeId
  }
  const { data } = await httpClient.get<EligibleEmployee[]>('/api/shifts/eligible', { params })
  return Array.isArray(data) ? data : []
}

export async function createManualShift(request: ManualShiftRequest): Promise<BackendShift> {
  const { data } = await httpClient.post<BackendShift>('/api/shifts/manual', request)
  return data
}

export async function deleteShift(shiftId: number, storeId?: number): Promise<void> {
  const params = typeof storeId === 'number' ? { storeId } : undefined
  await httpClient.delete(`/api/shifts/${shiftId}`, { params })
}

