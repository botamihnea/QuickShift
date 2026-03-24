import axios from 'axios'
import type { BackendShift, GenerateScheduleResponse } from '../types'

const BACKEND_URL = 'http://localhost:8080/api/shifts/generate'
const SHIFTS_URL = 'http://localhost:8080/api/shifts'

export async function generateNextMonthShifts(): Promise<GenerateScheduleResponse> {
  const { data } = await axios.post<GenerateScheduleResponse>(BACKEND_URL)
  return data
}

export async function getAllShifts(): Promise<BackendShift[]> {
  const result = await axios.get<BackendShift[] | undefined>(SHIFTS_URL)
  return Array.isArray(result.data) ? result.data : []
}
