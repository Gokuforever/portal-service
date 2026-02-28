package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SettlementStatus {
    PENDING(0, "Pending", "Settlement pending"),
    EMAIL_SENT(1, "Email Sent", "Weekly report email sent"),
    PAID(2, "Paid", "Settlement completed"),
    DISPUTED(3, "Disputed", "Seller raised dispute"),
    RESOLVED(4, "Resolved", "Dispute resolved");
    
    private final int id;
    private final String displayName;
    private final String description;
    
    public static SettlementStatus getById(int id) {
        for (SettlementStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }
        return null;
    }
}
