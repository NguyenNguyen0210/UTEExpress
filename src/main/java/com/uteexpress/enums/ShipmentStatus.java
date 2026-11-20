package com.uteexpress.enums;

public enum ShipmentStatus {
    PENDING_PAYMENT("Chờ thanh toán", "bg-warning-subtle text-warning-emphasis"),
    CREATED("Chờ lấy hàng", "bg-info-subtle text-info-emphasis"),
    ASSIGNED_PICKUP("Đã phân công lấy", "bg-info-subtle text-info-emphasis"),
    PICKED_UP("Đã lấy hàng", "bg-primary-subtle text-primary"),
    IN_ORIGIN_HUB("Tại bưu cục gửi", "bg-primary-subtle text-primary"),
    IN_TRANSIT("Đang trung chuyển", "bg-primary-subtle text-primary"),
    IN_DEST_HUB("Tại bưu cục nhận", "bg-primary-subtle text-primary"),
    OUT_FOR_DELIVERY("Đang giao hàng", "badge-out-for-delivery"),
    DELIVERED("Giao thành công", "bg-success-subtle text-success"),
    DELIVERY_FAILED("Giao thất bại", "bg-danger-subtle text-danger"),
    RETURNING("Đang hoàn hàng", "bg-danger-subtle text-danger"),
    RETURNED("Đã hoàn hàng", "bg-danger-subtle text-danger"),
    CANCELLED("Đã hủy", "bg-secondary-subtle text-secondary");

    private final String label;
    private final String badgeClass;

    ShipmentStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
