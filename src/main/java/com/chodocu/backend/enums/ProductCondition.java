package com.chodocu.backend.enums;

public enum ProductCondition {
    NEW_100("Mới 100%", "Chưa bóc hộp/sử dụng"),
    LIKE_NEW_95("Như mới (95-99%)", "Dùng vài lần, giữ kĩ"),
    GOOD_85("Tốt (80-90%)", "Có xước nhẹ bề mặt"),
    USABLE("Còn dùng tốt", "Pass rẻ cho bạn cần");

    private final String label;
    private final String description;

    ProductCondition(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
