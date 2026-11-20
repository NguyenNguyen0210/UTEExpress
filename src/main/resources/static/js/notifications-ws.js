/**
 * UTEExpress Real-time Notification Client (SockJS + STOMP)
 * Connects to /ws, fetches initial notifications from /api/v1/notifications,
 * subscribes to /user/queue/notifications, updates badge & dropdown, and displays Bootstrap 5 Toasts.
 */
(function () {
    'use strict';

    let stompClient = null;
    let unreadCount = 0;

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function updateBadge(count) {
        unreadCount = Math.max(0, count);
        const badge = document.getElementById('notificationUnreadBadge');
        if (!badge) return;
        badge.textContent = unreadCount > 99 ? '99+' : String(unreadCount);
        if (unreadCount > 0) {
            badge.classList.remove('d-none');
        } else {
            badge.classList.add('d-none');
        }
    }

    function formatTimestamp(raw) {
        if (!raw) return 'Vừa xong';
        const d = new Date(raw);
        if (isNaN(d.getTime())) return String(raw);
        const pad = function (n) { return n < 10 ? '0' + n : String(n); };
        return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ' ' + pad(d.getDate()) + '/' + pad(d.getMonth() + 1);
    }

    function buildNotificationItemHtml(item) {
        const isUnread = item.isRead === false || item.read === false || item.isRead === undefined;
        const bgClass = isUnread ? 'bg-light fw-semibold' : '';
        const title = escapeHtml(item.title || 'Thông báo hệ thống');
        const message = escapeHtml(item.message || '');
        const createdAt = escapeHtml(formatTimestamp(item.createdAt));
        const link = item.linkUrl || item.targetUrl || '#';

        return `
            <a class="list-group-item list-group-item-action py-2 px-3 notification-item ${bgClass}"
               data-id="${escapeHtml(item.id || '')}"
               href="${escapeHtml(link)}">
                <div class="d-flex justify-content-between align-items-center gap-2 mb-1">
                    <span class="small text-primary fw-bold text-truncate">${title}</span>
                    <small class="text-muted text-nowrap flex-shrink-0 font-mono" style="font-size: 0.72rem;">${createdAt}</small>
                </div>
                <div class="small text-secondary">${message}</div>
            </a>
        `;
    }

    function renderNotificationList(items, explicitUnreadCount) {
        const listEl = document.getElementById('notificationDropdownList');
        if (!listEl) return;

        if (!Array.isArray(items) || items.length === 0) {
            listEl.innerHTML = `
                <div class="p-3 text-center text-muted small" id="emptyNotificationPlaceholder">
                    Chưa có thông báo mới
                </div>
            `;
            updateBadge(0);
            return;
        }

        let unread = 0;
        const html = items.map(function (item) {
            if (item.isRead === false || item.read === false) {
                unread++;
            }
            return buildNotificationItemHtml(item);
        }).join('');

        listEl.innerHTML = html;
        updateBadge(typeof explicitUnreadCount === 'number' ? explicitUnreadCount : unread);
    }

    function prependNotification(item) {
        const listEl = document.getElementById('notificationDropdownList');
        if (listEl) {
            const placeholder = document.getElementById('emptyNotificationPlaceholder');
            if (placeholder) {
                placeholder.remove();
            }
            listEl.insertAdjacentHTML('afterbegin', buildNotificationItemHtml(Object.assign({ isRead: false }, item)));
        }
        updateBadge(unreadCount + 1);
    }

    function showToast(item) {
        let container = document.getElementById('wsToastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'wsToastContainer';
            container.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            container.style.zIndex = '1080';
            document.body.appendChild(container);
        }

        const title = escapeHtml(item.title || 'Thông báo mới');
        const message = escapeHtml(item.message || '');
        const link = item.linkUrl || item.targetUrl || '';

        const toastEl = document.createElement('div');
        toastEl.className = 'toast align-items-center border-0 shadow';
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');

        toastEl.innerHTML = `
            <div class="toast-header bg-primary text-white">
                <i class="bi bi-bell-fill me-2 text-warning"></i>
                <strong class="me-auto">${title}</strong>
                <button type="button" class="btn-close btn-close-white ms-2 mb-1" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
            <div class="toast-body bg-white text-dark">
                <div>${message}</div>
                ${link ? `<div class="mt-2"><a href="${escapeHtml(link)}" class="btn btn-sm btn-ute-orange py-0 px-2">Xem ngay</a></div>` : ''}
            </div>
        `;

        container.appendChild(toastEl);

        if (window.bootstrap && window.bootstrap.Toast) {
            const bsToast = new window.bootstrap.Toast(toastEl, { delay: 5000 });
            bsToast.show();
        } else {
            toastEl.classList.add('show');
            setTimeout(function () {
                toastEl.remove();
            }, 5000);
        }
    }

    function fetchInitialNotifications() {
        fetch('/api/v1/notifications', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (response) {
                if (!response.ok) return null;
                return response.json();
            })
            .then(function (payload) {
                if (!payload) return;
                const items = Array.isArray(payload)
                    ? payload
                    : (payload.notifications || payload.data || payload.items || []);
                renderNotificationList(items, payload.unreadCount);
            })
            .catch(function () {
                // Gracefully ignore when user is unauthenticated
            });
    }

    function bindMarkAllReadButton() {
        const btn = document.getElementById('markAllNotificationsReadBtn');
        if (!btn) return;

        btn.addEventListener('click', function (e) {
            e.preventDefault();
            fetch('/api/v1/notifications/read-all', {
                method: 'POST',
                headers: { 'Accept': 'application/json' },
                credentials: 'same-origin'
            })
                .then(function (res) {
                    if (res.ok) {
                        updateBadge(0);
                        document.querySelectorAll('#notificationDropdownList .notification-item').forEach(function (el) {
                            el.classList.remove('bg-light', 'fw-semibold');
                        });
                    }
                })
                .catch(function () {});
        });
    }

    function connectWebSocket() {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            return;
        }

        try {
            const socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            stompClient.debug = null;

            stompClient.connect({}, function () {
                stompClient.subscribe('/user/queue/notifications', function (frame) {
                    if (!frame || !frame.body) return;
                    try {
                        const notification = JSON.parse(frame.body);
                        prependNotification(notification);
                        showToast(notification);
                    } catch (err) {}
                });
            }, function () {});
        } catch (e) {}
    }

    function bindNotificationItemClicks() {
        const listEl = document.getElementById('notificationDropdownList');
        if (!listEl) return;

        listEl.addEventListener('click', function (e) {
            const itemEl = e.target.closest('.notification-item');
            if (!itemEl) return;
            const id = itemEl.getAttribute('data-id');
            if (id && itemEl.classList.contains('fw-semibold')) {
                itemEl.classList.remove('bg-light', 'fw-semibold');
                updateBadge(unreadCount - 1);
                fetch('/api/v1/notifications/' + encodeURIComponent(id) + '/read', {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' },
                    credentials: 'same-origin',
                    keepalive: true
                }).catch(function () {});
            }
        });
    }

    function init() {
        bindMarkAllReadButton();
        bindNotificationItemClicks();
        fetchInitialNotifications();
        connectWebSocket();
    }

    window.UteNotificationsWs = {
        init: init,
        refresh: fetchInitialNotifications,
        showToast: showToast
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
