import httpClient from './httpClient'
import type { CreateStoreRequest, StoreStaffResponse, StoreSummary } from '../types'

export async function getStores(): Promise<StoreSummary[]> {
  const { data } = await httpClient.get<StoreSummary[] | undefined>('/api/stores')
  return Array.isArray(data) ? data : []
}

export async function createStore(request: CreateStoreRequest): Promise<StoreSummary> {
  const { data } = await httpClient.post<StoreSummary>('/api/admin/stores', request)
  return data
}

export async function getStoreStaff(storeId: number): Promise<StoreStaffResponse> {
  const { data } = await httpClient.get<StoreStaffResponse>(`/api/admin/stores/${storeId}/staff`)
  return data
}

export async function updateStoreThreshold(storeId: number, busyDaySalesThreshold: number): Promise<StoreSummary> {
  const { data } = await httpClient.put<StoreSummary>(`/api/admin/stores/${storeId}/threshold`, {
    busyDaySalesThreshold,
  })
  return data
}

export async function updateMyStoreThreshold(busyDaySalesThreshold: number): Promise<StoreSummary> {
  const { data } = await httpClient.put<StoreSummary>('/api/stores/threshold', { busyDaySalesThreshold })
  return data
}
