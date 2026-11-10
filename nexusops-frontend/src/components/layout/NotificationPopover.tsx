import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Bell,
  Check,
  CheckCheck,
  FileText,
  ShieldAlert,
  ListTodo,
  BookOpen,
  GitBranch,
  Info,
  Clock,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { ApiResponse, PageResponse, NotificationResponseDto } from '@/types/api';

export const NotificationPopover: React.FC = () => {
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<NotificationResponseDto[]>([]);
  const [loading, setLoading] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  const fetchUnreadCount = useCallback(async () => {
    try {
      const res = await apiClient.get<any, ApiResponse<any>>('/notifications/unread-count');
      if (res && res.data !== undefined) {
        let count = 0;
        if (typeof res.data === 'number') {
          count = res.data;
        } else if (res.data && typeof res.data.unreadCount === 'number') {
          count = res.data.unreadCount;
        } else if (res.data && typeof res.data.count === 'number') {
          count = res.data.count;
        }
        setUnreadCount(count);
      }
    } catch {
      // Silently ignore background polling errors
    }
  }, []);

  const fetchNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get<any, ApiResponse<PageResponse<NotificationResponseDto>>>(
        '/notifications/my?page=0&size=20'
      );
      if (res?.data?.content) {
        setNotifications(res.data.content);
        const unreadItems = res.data.content.filter((n) => !n.read).length;
        setUnreadCount((prev) => Math.max(prev, unreadItems));
      }
    } catch (err) {
      console.error('Failed to load notifications', err);
    } finally {
      setLoading(false);
    }
  }, []);

  // Poll unread count every 15s and fetch on window focus
  useEffect(() => {
    fetchUnreadCount();
    // Also fetch initial notifications on mount to ensure instant accurate count
    fetchNotifications();

    const interval = setInterval(fetchUnreadCount, 15000);
    const onFocus = () => fetchUnreadCount();
    window.addEventListener('focus', onFocus);
    return () => {
      clearInterval(interval);
      window.removeEventListener('focus', onFocus);
    };
  }, [fetchUnreadCount, fetchNotifications]);

  // Refresh notifications list when opening popover
  useEffect(() => {
    if (isOpen) {
      fetchNotifications();
    }
  }, [isOpen, fetchNotifications]);

  // Handle outside click to close
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const markAsRead = async (id: string, e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    try {
      await apiClient.post(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.error('Failed to mark notification as read', err);
    }
  };

  const markAllAsRead = async () => {
    try {
      await apiClient.post('/notifications/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch (err) {
      console.error('Failed to mark all notifications as read', err);
    }
  };

  const handleNotificationClick = async (notif: NotificationResponseDto) => {
    if (!notif.read) {
      await markAsRead(notif.id);
    }
    setIsOpen(false);
    if (notif.linkUrl) {
      navigate(notif.linkUrl);
    }
  };

  const formatRelativeTime = (isoString: string) => {
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffMinutes = Math.floor(diffMs / (1000 * 60));
      const diffHours = Math.floor(diffMinutes / 60);
      const diffDays = Math.floor(diffHours / 24);

      if (diffMinutes < 1) return 'Just now';
      if (diffMinutes < 60) return `${diffMinutes}m ago`;
      if (diffHours < 24) return `${diffHours}h ago`;
      return `${diffDays}d ago`;
    } catch {
      return '';
    }
  };

  const getEventIcon = (eventType: string) => {
    if (eventType.startsWith('REQUEST_')) {
      return <FileText className="h-4 w-4 text-indigo-600" />;
    }
    if (eventType.startsWith('APPROVAL_')) {
      return <ShieldAlert className="h-4 w-4 text-amber-600" />;
    }
    if (eventType.startsWith('TASK_')) {
      return <ListTodo className="h-4 w-4 text-purple-600" />;
    }
    if (eventType.startsWith('DOCUMENT_')) {
      return <BookOpen className="h-4 w-4 text-cyan-600" />;
    }
    if (eventType.startsWith('WORKFLOW_')) {
      return <GitBranch className="h-4 w-4 text-emerald-600" />;
    }
    return <Info className="h-4 w-4 text-slate-500" />;
  };

  return (
    <div className="relative" ref={popoverRef}>
      {/* Bell Button with Unread Badge */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors focus:outline-none"
        title="Notifications"
        aria-label="Open notifications"
      >
        <Bell className="h-5 w-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-extrabold text-white bg-rose-500 rounded-full ring-2 ring-white shadow-sm animate-pulse">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {/* Popover Card */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-white rounded-2xl shadow-2xl border border-slate-200 z-50 animate-in fade-in zoom-in-95 duration-100 overflow-hidden">
          {/* Header */}
          <div className="px-4 py-3 bg-slate-50/80 border-b border-slate-200/80 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-slate-800">Notifications</span>
              {unreadCount > 0 ? (
                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 border border-rose-200">
                  {unreadCount} unread
                </span>
              ) : (
                <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-500">
                  All caught up
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                onClick={markAllAsRead}
                className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1 transition-colors"
              >
                <CheckCheck className="h-3.5 w-3.5" />
                <span>Mark all read</span>
              </button>
            )}
          </div>

          {/* List */}
          <div className="max-h-[380px] overflow-y-auto divide-y divide-slate-100">
            {loading && notifications.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-400">Loading notifications...</div>
            ) : notifications.length === 0 ? (
              <div className="py-10 px-4 text-center">
                <div className="h-10 w-10 mx-auto rounded-full bg-emerald-50 border border-emerald-100 flex items-center justify-center text-emerald-600 mb-2">
                  <Check className="h-5 w-5" />
                </div>
                <div className="text-xs font-semibold text-slate-700">All caught up!</div>
                <div className="text-[11px] text-slate-400 mt-0.5">
                  No notifications to review at this moment.
                </div>
              </div>
            ) : (
              notifications.map((notif) => (
                <div
                  key={notif.id}
                  onClick={() => handleNotificationClick(notif)}
                  className={`p-3.5 flex items-start gap-3 hover:bg-slate-50/80 cursor-pointer transition-colors ${
                    !notif.read ? 'bg-indigo-50/40' : ''
                  }`}
                >
                  <div className="p-2 rounded-xl bg-slate-100 shrink-0 mt-0.5">
                    {getEventIcon(notif.eventType)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-xs font-bold text-slate-800 truncate">
                        {notif.title}
                      </div>
                      <div className="flex items-center gap-1.5 shrink-0">
                        <span className="text-[10px] text-slate-400 font-medium flex items-center gap-1">
                          <Clock className="h-2.5 w-2.5" />
                          {formatRelativeTime(notif.createdAt)}
                        </span>
                        {!notif.read && (
                          <span
                            className="h-2 w-2 rounded-full bg-indigo-600 shrink-0 ring-2 ring-indigo-200"
                            title="Unread"
                          />
                        )}
                      </div>
                    </div>
                    <p className="text-xs text-slate-600 mt-1 line-clamp-2 leading-relaxed">
                      {notif.message}
                    </p>
                  </div>
                  {!notif.read && (
                    <button
                      onClick={(e) => markAsRead(notif.id, e)}
                      className="p-1 text-slate-400 hover:text-indigo-600 rounded hover:bg-slate-200/60 transition-colors"
                      title="Mark as read"
                    >
                      <Check className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};
