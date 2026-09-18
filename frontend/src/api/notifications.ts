import { api } from './client';
import type { AppNotification, PageResponse } from '@/lib/types';

/**
 * Notifications are always the caller's own: the API takes the recipient from
 * the token, so there is no user parameter to pass or to get wrong.
 */
export async function listNotifications(): Promise<AppNotification[]> {
  const { data } = await api.get<PageResponse<AppNotification>>('/notifications');
  return data.content;
}

export async function unreadCount(): Promise<number> {
  const { data } = await api.get<{ unread: number }>('/notifications/unread-count');
  return data.unread;
}

export async function markRead(id: string): Promise<void> {
  await api.patch(`/notifications/${id}/read`);
}

export async function markAllRead(): Promise<void> {
  await api.patch('/notifications/read-all');
}
