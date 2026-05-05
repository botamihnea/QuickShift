import type { Event } from 'react-big-calendar'

export type AuthRequest = {
  email: string
  password: string
}

export type RegisterRequest = {
  fullName: string
  email: string
  password: string
  shiftPreference: 'MORNING' | 'EVENING' | 'ANY'
  contractType: 'FULL_TIME_8H' | 'PART_TIME_6H' | 'PART_TIME_4H'
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

export type ManagerSummary = {
  id: number
  email: string
}

export type EmployeeSummary = {
  id: number
  fullName: string
  contractType: 'FULL_TIME_8H' | 'PART_TIME_6H' | 'PART_TIME_4H'
  shiftPreference: 'MORNING' | 'EVENING' | 'ANY'
  remainingLeaveDays: number | null
  holidayRecoveryHours: number | null
}

export type StoreStaffResponse = {
  manager: ManagerSummary | null
  employees: EmployeeSummary[]
}

export type NotificationItem = {
  id: number
  message: string
  createdAt: string
  read: boolean
  storeId: number | null
  storeName: string | null
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
