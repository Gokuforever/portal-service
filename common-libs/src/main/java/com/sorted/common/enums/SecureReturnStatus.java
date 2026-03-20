package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Status lifecycle for secure buy/return process.
 * Tracks the complete journey from scheduling to refund completion.
 */
@Getter
@AllArgsConstructor
public enum SecureReturnStatus {

    SCHEDULED(1, "Return Scheduled"),
    PICKUP_PENDING(2, "Awaiting Pickup"),
    PICKUP_ASSIGNED(3, "Delivery Partner Assigned"),
    IN_TRANSIT(5, "In Transit to Seller"),
    DELIVERED_TO_SELLER(6, "Delivered to Seller"),
    UNDER_APPRAISAL(7, "Seller Reviewing Items"),
    APPRAISAL_COMPLETED(8, "Appraisal Done"),
    REFUND_PENDING(9, "Refund Processing"),
    REFUND_COMPLETED(10, "Refund Completed"),
    REFUND_NOT_APPLICABLE(11, "Refund Not Applicable"),
    FAILED(12, "Process Failed"),
    CANCELLED(13, "Cancelled by User/System"),
    REFUND_FAILED(14, "Refund Failed");

    private final Integer id;
    private final String description;

    /**
     * Check if the status is a terminal state (no further transitions)
     */
    public boolean isTerminal() {
        return this == REFUND_COMPLETED || this == FAILED || this == CANCELLED;
    }

    /**
     * Check if refund can be initiated in this status
     */
    public boolean canInitiateRefund() {
        return this == APPRAISAL_COMPLETED;
    }

    /**
     * Check if the return can be rescheduled in this status
     */
    public boolean canReschedule() {
        return this == SCHEDULED;
    }
}
