/**
 * UTEExpress - Client-side Order Pricing & Summary Helper
 * Supports 2-column Shipment Creation Form (/customer/shipments/new)
 * and Public Estimate Form (/estimate).
 */
window.UteOrderPricing = (function () {
    'use strict';

    const ROUTE_TYPE_LABELS = {
        INTRA_PROVINCE: 'Nội tỉnh',
        INTRA_REGION: 'Nội miền',
        INTER_REGION: 'Liên miền'
    };

    function formatVnd(amount) {
        const val = Number(amount) || 0;
        return new Intl.NumberFormat('vi-VN').format(Math.round(val)) + ' đ';
    }

    function setText(selector, text) {
        const el = document.querySelector(selector);
        if (el) {
            el.textContent = text;
        }
    }

    function getCheckedValue(name, fallback) {
        const checked = document.querySelector('input[name="' + name + '"]:checked');
        if (checked) return checked.value;
        const select = document.querySelector('select[name="' + name + '"]');
        if (select) return select.value;
        return fallback;
    }

    function getNumberValue(selector, fallback) {
        const el = document.querySelector(selector);
        if (!el) return fallback;
        const parsed = parseFloat(el.value);
        return isNaN(parsed) || parsed < 0 ? fallback : parsed;
    }

    function init(userOptions) {
        const options = Object.assign({
            apiEndpoint: '/api/v1/public/estimate',
            debounceMs: 250
        }, userOptions || {});

        let debounceTimer = null;

        const fieldSelectors = [
            '#senderProvince',
            '#receiverProvince',
            '#actualWeightKg',
            '#lengthCm',
            '#widthCm',
            '#heightCm',
            '#codAmount',
            'input[name="serviceTypeId"]',
            'select[name="serviceTypeId"]',
            'input[name="payerType"]',
            'input[name="paymentMethod"]'
        ];

        function updatePaymentControls() {
            const payerType = getCheckedValue('payerType', 'SENDER');
            const paymentMethod = getCheckedValue('paymentMethod', 'CASH');
            const vnpayOption = document.querySelector('#vnpayMethodWrapper, [data-payment-option="VNPAY"]');
            const vnpayRadio = document.querySelector('input[name="paymentMethod"][value="VNPAY"]');
            const cashRadio = document.querySelector('input[name="paymentMethod"][value="CASH"]');
            const submitBtnLabel = document.querySelector('#submitOrderBtnText, #submitOrderBtn');

            if (payerType === 'RECEIVER') {
                if (vnpayRadio && vnpayRadio.checked && cashRadio) {
                    cashRadio.checked = true;
                }
                if (vnpayRadio) {
                    vnpayRadio.disabled = true;
                }
                if (vnpayOption) {
                    vnpayOption.classList.add('opacity-50');
                }
            } else {
                if (vnpayRadio) {
                    vnpayRadio.disabled = false;
                }
                if (vnpayOption) {
                    vnpayOption.classList.remove('opacity-50');
                }
            }

            const effectiveMethod = getCheckedValue('paymentMethod', 'CASH');
            if (submitBtnLabel) {
                if (payerType === 'SENDER' && effectiveMethod === 'VNPAY') {
                    submitBtnLabel.textContent = 'Tạo đơn & Thanh toán VNPay';
                } else {
                    submitBtnLabel.textContent = 'Tạo vận đơn';
                }
            }
        }

        function updateRouteHeader() {
            const senderEl = document.querySelector('#senderProvince');
            const receiverEl = document.querySelector('#receiverProvince');
            const senderText = senderEl && senderEl.selectedOptions && senderEl.selectedOptions[0]
                ? senderEl.selectedOptions[0].textContent.trim()
                : (senderEl && senderEl.value ? senderEl.value : '---');
            const receiverText = receiverEl && receiverEl.selectedOptions && receiverEl.selectedOptions[0]
                ? receiverEl.selectedOptions[0].textContent.trim()
                : (receiverEl && receiverEl.value ? receiverEl.value : '---');

            if (senderEl && receiverEl && senderEl.value && receiverEl.value) {
                setText('#summaryRoute', senderText + ' → ' + receiverText);
            } else {
                setText('#summaryRoute', 'Chưa chọn tuyến');
            }
        }

        function calculateAndRender() {
            updatePaymentControls();
            updateRouteHeader();

            const senderProvince = (document.querySelector('#senderProvince') || {}).value || '';
            const receiverProvince = (document.querySelector('#receiverProvince') || {}).value || '';
            const serviceTypeId = getCheckedValue('serviceTypeId', '');
            const actualWeightKg = getNumberValue('#actualWeightKg', 0);
            const lengthCm = getNumberValue('#lengthCm', 0);
            const widthCm = getNumberValue('#widthCm', 0);
            const heightCm = getNumberValue('#heightCm', 0);
            const codAmount = getNumberValue('#codAmount', 0);
            const payerType = getCheckedValue('payerType', 'SENDER');

            if (!senderProvince || !receiverProvince || !serviceTypeId || actualWeightKg <= 0) {
                return;
            }

            const payload = {
                senderProvinceCode: senderProvince,
                receiverProvinceCode: receiverProvince,
                serviceTypeId: Number(serviceTypeId) || serviceTypeId,
                actualWeightKg: actualWeightKg,
                lengthCm: lengthCm > 0 ? lengthCm : 10,
                widthCm: widthCm > 0 ? widthCm : 10,
                heightCm: heightCm > 0 ? heightCm : 10,
                codAmount: codAmount
            };

            const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
            const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
            const headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            };
            if (csrfTokenMeta && csrfHeaderMeta) {
                headers[csrfHeaderMeta.getAttribute('content')] = csrfTokenMeta.getAttribute('content');
            }

            fetch(options.apiEndpoint, {
                method: 'POST',
                headers: headers,
                body: JSON.stringify(payload)
            })
                .then(function (res) {
                    if (!res.ok) throw new Error('Estimate request failed: ' + res.status);
                    return res.json();
                })
                .then(function (json) {
                    const data = (json && json.data) ? json.data : json;
                    if (!data) return;

                    const routeTypeLabel = ROUTE_TYPE_LABELS[data.routeType] || data.routeTypeLabel || data.routeType || '---';
                    const chargeableWeight = data.chargeableWeightKg !== undefined ? data.chargeableWeightKg : actualWeightKg;
                    const shippingFee = Number(data.shippingFee || data.totalShippingFee || 0);
                    const codFee = Number(data.codFee || 0);
                    const totalFee = Number(data.totalFee !== undefined ? data.totalFee : (shippingFee + codFee));
                    const receiverCollect = payerType === 'RECEIVER' ? (codAmount + totalFee) : codAmount;

                    setText('#summaryRouteType', routeTypeLabel);
                    setText('#summaryChargeableWeight', Number(chargeableWeight).toFixed(2) + ' kg');
                    setText('#summaryShippingFee', formatVnd(shippingFee));
                    setText('#summaryCodFee', formatVnd(codFee));
                    setText('#summaryTotalFee', formatVnd(totalFee));
                    setText('#summaryReceiverCollect', formatVnd(receiverCollect));

                    if (typeof options.onEstimateSuccess === 'function') {
                        options.onEstimateSuccess(data);
                    }
                })
                .catch(function (err) {
                    if (typeof options.onEstimateError === 'function') {
                        options.onEstimateError(err);
                    }
                });
        }

        function scheduleCalculate() {
            updatePaymentControls();
            updateRouteHeader();
            if (debounceTimer) clearTimeout(debounceTimer);
            debounceTimer = setTimeout(calculateAndRender, options.debounceMs);
        }

        fieldSelectors.forEach(function (selector) {
            document.querySelectorAll(selector).forEach(function (el) {
                el.addEventListener('change', scheduleCalculate);
                el.addEventListener('input', scheduleCalculate);
            });
        });

        updatePaymentControls();
        updateRouteHeader();
        calculateAndRender();

        return {
            refresh: calculateAndRender,
            formatVnd: formatVnd
        };
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (document.querySelector('[data-auto-order-pricing="true"]') || document.querySelector('#senderProvince')) {
            init();
        }
    });

    return {
        init: init,
        formatVnd: formatVnd
    };
})();
