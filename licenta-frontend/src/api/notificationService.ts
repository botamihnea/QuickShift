import httpClient from './httpClient'
import type { NotificationItem } from '../types'

export async function getNotifications(): Promise<NotificationItem[]> {
  const { data } = await httpClient.get<NotificationItem[]>('/api/notifications')
  return Array.isArray(data) ? data : []
}

export async function markNotificationRead(notificationId: number): Promise<void> {
  await httpClient.put(`/api/notifications/${notificationId}/read`)
}
