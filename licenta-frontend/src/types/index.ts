import type { Event } from 'react-big-calendar'

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
