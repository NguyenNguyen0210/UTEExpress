package com.uteexpress.enums;

public enum InspectionPolicy {
    ALLOW_VIEW_NO_TRY("Cho xem hàng, không cho thử"),
    ALLOW_TRY("Cho thử hàng"),
    NO_VIEW("Không cho xem hàng");

    private final String label;

    InspectionPolicy(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
