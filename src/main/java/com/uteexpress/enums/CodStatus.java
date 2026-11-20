package com.uteexpress.enums;

public enum CodStatus {
    NONE("Không có COD"),
    PENDING("Chờ thu COD"),
    HELD_BY_SHIPPER("Shipper đang giữ"),
    COLLECTED_BY_HUB("Bưu cục đã thu"),
    SETTLED("Đã đối soát");

    private final String label;

    CodStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
