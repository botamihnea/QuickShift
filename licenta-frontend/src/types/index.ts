import type { Event } from 'react-big-calendar'

export type AuthRequest = {
  email: string
  password: string
}

export type RegisterRequest = {
  email: string
  password: string
  storeId: number
}

export type AuthResponse = {
  token: string
}

export type UserRole = 'ADMIN' | 'MANAGER' | 'EMPLOYEE'

export type AuthenticatedUser = {
  email: string
  role: UserRole
  storeId: number | null
  storeName: string | null
}

export type StoreSummary = {
  id: number
  storeName: string
}

export type CreateStoreRequest = {
  storeName: string
  address: string
  busyDaySalesThreshold: number
}

export type GenerateScheduleResponse = {
  targetYear: number
  targetMonth: number
  forecastDaysUsed: number
  generatedShifts: number
  message: string
}

export type BackendShift = {
  id: number
  shiftDate: string
  shiftType: string
  employee: {
    id: number
    fullName: string
  }
}

export type ShiftCalendarEvent = Event & {
  resource: {
    isPartTime: boolean
    rawShiftType: string
    employeeName: string
    compactEmployeeName: string
    timeRange: string
  }
}
