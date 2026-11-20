package com.uteexpress.enums;

public enum SettlementStatus {
    PENDING("Chờ chuyển khoản"),
    SETTLED("Đã thanh toán");

    private final String label;

    SettlementStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
