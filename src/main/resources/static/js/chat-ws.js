/**
 * UTEExpress Real-time Shipment Chat Client (SockJS + STOMP)
 * Exposes UteChatWs.init(shipmentId, currentUserId)
 * Loads chat history from GET /api/v1/chat/{shipmentId}/messages,
 * subscribes to /topic/chat/{shipmentId}, and sends messages to /app/chat/{shipmentId}.
 */
(function () {
    'use strict';

    let stompClient = null;
    let activeShipmentId = null;
    let activeUserId = null;

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function scrollToBottom(box) {
        if (!box) return;
        box.scrollTop = box.scrollHeight;
    }

    function buildChatBubbleHtml(msg) {
        const isOwn = activeUserId !== null &&
            activeUserId !== undefined &&
            String(msg.senderId) === String(activeUserId);

        const alignClass = isOwn ? 'justify-content-end' : 'justify-content-start';
        const bubbleClass = isOwn ? 'bg-primary text-white' : 'bg-light text-dark border';
        const senderName = escapeHtml(msg.senderName || (isOwn ? 'Bạn' : 'Người dùng'));
        const senderRole = msg.senderRole ? `<span class="badge bg-secondary ms-1" style="font-size: 0.65rem;">${escapeHtml(msg.senderRole)}</span>` : '';
        const content = escapeHtml(msg.content || '');
        const createdAt = escapeHtml(msg.createdAt || 'Vừa xong');

        return `
            <div class="d-flex ${alignClass} mb-2 chat-message-item" data-message-id="${escapeHtml(msg.id || '')}">
                <div class="p-2 px-3 rounded-3 shadow-sm ${bubbleClass}" style="max-width: 78%;">
                    <div class="d-flex justify-content-between align-items-center mb-1 gap-2">
                        <small class="fw-bold ${isOwn ? 'text-white-50' : 'text-primary'}">${senderName}${senderRole}</small>
                        <small class="${isOwn ? 'text-white-50' : 'text-muted'}" style="font-size: 0.7rem;">${createdAt}</small>
                    </div>
                    <div class="small text-break">${content}</div>
                </div>
            </div>
        `;
    }

    function appendMessage(msg) {
        const box = document.getElementById('chatMessagesBox');
        if (!box) return;

        const emptyPlaceholder = box.querySelector('.chat-empty-placeholder');
        if (emptyPlaceholder) {
            emptyPlaceholder.remove();
        }

        box.insertAdjacentHTML('beforeend', buildChatBubbleHtml(msg));
        scrollToBottom(box);
    }

    function renderHistory(messages) {
        const box = document.getElementById('chatMessagesBox');
        if (!box) return;

        if (!Array.isArray(messages) || messages.length === 0) {
            box.innerHTML = `
                <div class="text-center text-muted py-4 small chat-empty-placeholder">
                    Chưa có tin nhắn trao đổi nào cho vận đơn này.
                </div>
            `;
            return;
        }

        box.innerHTML = messages.map(buildChatBubbleHtml).join('');
        scrollToBottom(box);
    }

    function fetchChatHistory(shipmentId) {
        fetch('/api/v1/chat/' + encodeURIComponent(shipmentId) + '/messages', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'same-origin'
        })
            .then(function (res) {
                if (!res.ok) return null;
                return res.json();
            })
            .then(function (payload) {
                if (!payload) return;
                const messages = Array.isArray(payload) ? payload : (payload.data || payload.items || []);
                renderHistory(messages);
            })
            .catch(function () {
                // Ignore if endpoint is not yet ready
            });
    }

    function connectAndSubscribe(shipmentId) {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            return;
        }

        try {
            const socket = new SockJS('/ws');
            stompClient = Stomp.over(socket);
            stompClient.debug = null;

            stompClient.connect({}, function () {
                stompClient.subscribe('/topic/chat/' + shipmentId, function (frame) {
                    if (!frame || !frame.body) return;
                    try {
                        const msg = JSON.parse(frame.body);
                        appendMessage(msg);
                    } catch (err) {
                        // Ignore malformed JSON frame
                    }
                });
            }, function () {
                stompClient = null;
            });
        } catch (e) {
            stompClient = null;
        }
    }

    function bindSendForm(shipmentId) {
        const form = document.getElementById('chatSendForm');
        if (!form) return;

        form.addEventListener('submit', function (e) {
            e.preventDefault();
            const input = form.querySelector('input[name="content"], textarea[name="content"], #chatMessageInput');
            if (!input) return;

            const content = input.value.trim();
            if (!content) return;

            if (stompClient && stompClient.connected) {
                stompClient.send('/app/chat/' + shipmentId, {}, JSON.stringify({ content: content }));
                input.value = '';
                input.focus();
            }
        });
    }

    function init(shipmentId, currentUserId) {
        if (!shipmentId) return;
        activeShipmentId = shipmentId;
        activeUserId = currentUserId;

        fetchChatHistory(shipmentId);
        connectAndSubscribe(shipmentId);
        bindSendForm(shipmentId);
    }

    window.UteChatWs = {
        init: init,
        appendMessage: appendMessage
    };
})();
