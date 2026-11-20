package com.uteexpress.enums;

public enum PayerType {
    SENDER("Người gửi trả"),
    RECEIVER("Người nhận trả");

    private final String label;

    PayerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
