package com.uteexpress.enums;

public enum PaymentMethod {
    CASH("Tiền mặt"),
    VNPAY("VNPay Sandbox");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
