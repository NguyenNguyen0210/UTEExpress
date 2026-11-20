/**
 * UTEExpress - Client-side Order Pricing & Summary Helper
 * Supports 2-column Shipment Creation Form (/customer/shipments/new)
 * and Public Estimate Form (/estimate) per 06-API-AND-ROUTES.md.
 */
window.UteOrderPricing = (function () {
    'use strict';

    function formatVnd(amount) {
        const val = Number(amount) || 0;
        return new Intl.NumberFormat('vi-VN').format(Math.round(val)) + ' Ä‘';
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
        if (!el || el.value === '' || el.value === null) return fallback;
        const parsed = parseFloat(el.value);
        return isNaN(parsed) || parsed < 0 ? fallback : parsed;
    }

    function init(userOptions) {
        const options = Object.assign({
            apiEndpoint: '/api/v1/public/estimate',
            debounceMs: 200
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
                    submitBtnLabel.textContent = 'Táº¡o Ä‘Æ¡n & Thanh toÃ¡n VNPay';
                } else {
                    submitBtnLabel.textContent = 'Táº¡o váº­n Ä‘Æ¡n';
                }
            }
        }

        function calculateAndRender() {
            updatePaymentControls();

            const senderProvince = (document.querySelector('#senderProvince') || {}).value || '';
            const receiverProvince = (document.querySelector('#receiverProvince') || {}).value || '';
            const selectedServiceTypeId = getCheckedValue('serviceTypeId', null);
            const actualWeightKg = getNumberValue('#actualWeightKg', 0);
            const lengthCm = getNumberValue('#lengthCm', null);
            const widthCm = getNumberValue('#widthCm', null);
            const heightCm = getNumberValue('#heightCm', null);
            const codAmount = getNumberValue('#codAmount', 0);
            const payerType = getCheckedValue('payerType', 'SENDER');

            if (!senderProvince || !receiverProvince || actualWeightKg <= 0) {
                return;
            }

            const payload = {
                senderProvince: senderProvince,
                receiverProvince: receiverProvince,
                actualWeightKg: actualWeightKg,
                lengthCm: lengthCm && lengthCm > 0 ? Math.round(lengthCm) : null,
                widthCm: widthCm && widthCm > 0 ? Math.round(widthCm) : null,
                heightCm: heightCm && heightCm > 0 ? Math.round(heightCm) : null,
                codAmount: Math.round(codAmount || 0),
                serviceTypeId: selectedServiceTypeId ? Number(selectedServiceTypeId) : null
            };

            fetch(options.apiEndpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(payload)
            })
                .then(function (res) {
                    if (!res.ok) throw new Error('Estimate request failed: ' + res.status);
                    return res.json();
                })
                .then(function (data) {
                    if (!data) return;

                    const originHub = data.originHubCode || senderProvince;
                    const destHub = data.destHubCode || receiverProvince;
                    setText('#summaryRoute', originHub + ' â†’ ' + destHub);
                    setText('#summaryRouteType', data.interProvince ? 'LiÃªn tá»‰nh' : 'Ná»™i vÃ¹ng');
                    setText('#summaryVolumetricWeight', Number(data.volumetricWeightKg || 0).toFixed(2) + ' kg');
                    setText('#summaryChargeableWeight', Number(data.chargeableWeightKg || actualWeightKg).toFixed(2) + ' kg');

                    const opts = Array.isArray(data.options) ? data.options : [];
                    let chosen = null;
                    if (selectedServiceTypeId && opts.length > 0) {
                        chosen = opts.find(function (o) {
                            return String(o.serviceTypeId) === String(selectedServiceTypeId);
                        }) || opts[0];
                    } else if (opts.length > 0) {
                        chosen = opts[0];
                    }

                    if (chosen) {
                        const shippingFee = Number(chosen.shippingFee || 0);
                        const codFee = Number(chosen.codFee || 0);
                        const totalFee = Number(chosen.totalFee !== undefined ? chosen.totalFee : (shippingFee + codFee));
                        const receiverCollect = payerType === 'RECEIVER' ? (codAmount + totalFee) : codAmount;

                        setText('#summaryShippingFee', formatVnd(shippingFee));
                        setText('#summaryCodFee', formatVnd(codFee));
                        setText('#summaryTotalFee', formatVnd(totalFee));
                        setText('#summaryReceiverCollect', formatVnd(receiverCollect));
                        setText('#summaryEstimatedDays', chosen.estimatedDays || '');
                    }

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

