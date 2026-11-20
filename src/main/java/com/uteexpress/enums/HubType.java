package com.uteexpress.enums;

public enum HubType {
    POST_OFFICE("Bưu cục"),
    SORTING_HUB("Kho trung chuyển");

    private final String label;

    HubType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
