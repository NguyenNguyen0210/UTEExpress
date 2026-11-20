package com.uteexpress.enums;

public enum Role {
    CUSTOMER("Khách hàng"),
    STAFF("Nhân viên Bưu cục"),
    SHIPPER("Nhân viên Giao nhận"),
    ADMIN("Quản trị viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
