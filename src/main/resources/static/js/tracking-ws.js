/**
 * UTEExpress Real-time Shipment Tracking Client (SockJS + STOMP)
 * Exposes UteTrackingWs.subscribe(trackingCodes, onUpdateCallback)
 * Subscribes to /topic/tracking/{code} and updates timeline & status badge in real time.
 */
(function () {
    'use strict';

    let stompClient = null;
    let isConnected = false;
    let pendingSubscriptions = [];

    const STATUS_BADGE_MAP = {
        'PENDING_PAYMENT':  { label: 'Chờ thanh toán',    className: 'bg-warning-subtle text-warning-emphasis' },
        'CREATED':          { label: 'Chờ lấy hàng',      className: 'bg-info-subtle text-info-emphasis' },
        'ASSIGNED_PICKUP':  { label: 'Đã phân công lấy',  className: 'bg-info-subtle text-info-emphasis' },
        'PICKED_UP':        { label: 'Đã lấy hàng',       className: 'bg-primary-subtle text-primary' },
        'IN_ORIGIN_HUB':    { label: 'Tại bưu cục gửi',   className: 'bg-primary-subtle text-primary' },
        'IN_TRANSIT':       { label: 'Đang trung chuyển', className: 'bg-primary-subtle text-primary' },
        'IN_DEST_HUB':      { label: 'Tại bưu cục nhận',  className: 'bg-primary-subtle text-primary' },
        'OUT_FOR_DELIVERY': { label: 'Đang giao hàng',    className: 'badge-out-for-delivery' },
        'DELIVERED':        { label: 'Giao thành công',   className: 'bg-success-subtle text-success' },
        'DELIVERY_FAILED':  { label: 'Giao thất bại',     className: 'bg-danger-subtle text-danger' },
        'RETURNING':        { label: 'Đang hoàn hàng',    className: 'bg-danger-subtle text-danger' },
        'RETURNED':         { label: 'Đã hoàn hàng',      className: 'bg-danger-subtle text-danger' },
        'CANCELLED':        { label: 'Đã hủy',            className: 'bg-secondary-subtle text-secondary' }
    };

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function updateStatusBadgeDom(code, payload) {
        const statusKey = payload.status || payload.newStatus || '';
        const statusInfo = STATUS_BADGE_MAP[statusKey] || {
            label: payload.statusLabel || statusKey || 'Cập nhật',
            className: 'bg-primary-subtle text-primary'
        };

        const badgeTargets = [
            document.getElementById('status-badge-' + code),
            document.getElementById('trackingStatusBadge')
        ];

        badgeTargets.forEach(function (badge) {
            if (!badge) return;
            badge.className = 'badge rounded-pill px-3 py-2 ' + statusInfo.className;
            badge.textContent = payload.statusLabel || statusInfo.label;
        });

        const hubTargets = [
            document.getElementById('current-hub-' + code),
            document.getElementById('trackingCurrentHub')
        ];
        if (payload.currentHubName) {
            hubTargets.forEach(function (el) {
                if (el) el.textContent = payload.currentHubName;
            });
        }
    }

    function updateTimelineDom(code, payload) {
        const timelineEl = document.getElementById('timeline-' + code) ||
            document.getElementById('trackingTimelineList');
        if (!timelineEl) return;

        const title = escapeHtml(payload.logTitle || payload.statusLabel || payload.status || 'Cập nhật trạng thái');
        const description = escapeHtml(payload.logDescription || payload.description || '');
        const hubName = escapeHtml(payload.currentHubName || payload.location || '');
        const timestamp = escapeHtml(payload.timestamp || payload.createdAt || 'Vừa xong');

        const itemHtml = `
            <li class="tracking-timeline-item timeline-flash p-2 rounded">
                <div class="d-flex w-100 justify-content-between align-items-center">
                    <div class="fw-bold text-primary">${title}</div>
                    <small class="text-muted font-mono">${timestamp}</small>
                </div>
                ${description ? `<div class="small text-secondary mt-1">${description}</div>` : ''}
                ${hubName ? `<div class="small text-muted mt-1"><i class="bi bi-geo-alt-fill text-warning me-1"></i>${hubName}</div>` : ''}
            </li>
        `;

        timelineEl.insertAdjacentHTML('afterbegin', itemHtml);
    }

    function subscribeInternal(code, onUpdateCallback) {
        if (!stompClient || !isConnected || !code) return;
        const normalizedCode = String(code).trim();
        if (!normalizedCode) return;

        stompClient.subscribe('/topic/tracking/' + normalizedCode, function (frame) {
            if (!frame || !frame.body) return;
            try {
                const payload = JSON.parse(frame.body);
                updateStatusBadgeDom(normalizedCode, payload);
                updateTimelineDom(normalizedCode, payload);
                if (typeof onUpdateCallback === 'function') {
                    onUpdateCallback(normalizedCode, payload);
                }
            } catch (err) {}
        });
    }

    function ensureConnection(callback) {
        if (isConnected && stompClient) {
            callback();
            return;
        }

        pendingSubscriptions.push(callback);
        if (stompClient) {
            return;
        }

        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            return;
        }

        try {
            const socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            stompClient.debug = null;

            stompClient.connect({}, function () {
                isConnected = true;
                while (pendingSubscriptions.length > 0) {
                    const fn = pendingSubscriptions.shift();
                    fn();
                }
            }, function () {
                isConnected = false;
                stompClient = null;
            });
        } catch (e) {
            isConnected = false;
            stompClient = null;
        }
    }

    function subscribe(trackingCodes, onUpdateCallback) {
        const codes = Array.isArray(trackingCodes) ? trackingCodes : [trackingCodes];
        const validCodes = codes.filter(function (c) {
            return c && String(c).trim().length > 0;
        });

        if (validCodes.length === 0) return;

        ensureConnection(function () {
            validCodes.forEach(function (code) {
                subscribeInternal(code, onUpdateCallback);
            });
        });
    }

    window.UteTrackingWs = {
        subscribe: subscribe
    };
})();
