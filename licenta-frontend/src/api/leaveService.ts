import httpClient from './httpClient'
import type { LeaveRequestCreateRequest, LeaveRequestDecisionRequest, LeaveRequestResponse } from '../types'

export async function requestLeave(request: LeaveRequestCreateRequest): Promise<LeaveRequestResponse> {
  const { data } = await httpClient.post<LeaveRequestResponse>('/api/leave-requests', request)
  return data
}

export async function approveLeaveRequest(requestId: number): Promise<LeaveRequestResponse> {
  const { data } = await httpClient.post<LeaveRequestResponse>(`/api/leave-requests/${requestId}/approve`)
  return data
}

export async function denyLeaveRequest(
  requestId: number,
  request?: LeaveRequestDecisionRequest,
): Promise<LeaveRequestResponse> {
  const { data } = await httpClient.post<LeaveRequestResponse>(`/api/leave-requests/${requestId}/deny`, request)
  return data
}
