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
    PICKUP_CONFIRMED(2, "Pickup Confirmed"),
    PICKUP_NOT_CONFIRMED(3, "Pickup Not Confirmed"),
    PICKUP_PENDING(4, "Awaiting Pickup"),
    PICKUP_ASSIGNED(5, "Delivery Partner Assigned"),
    IN_TRANSIT(6, "In Transit to Seller"),
    DELIVERED_TO_SELLER(7, "Delivered to Seller"),
    UNDER_APPRAISAL(8, "Seller Reviewing Items"),
    APPRAISAL_COMPLETED(9, "Appraisal Done"),
    REFUND_PENDING(10, "Refund Processing"),
    REFUND_COMPLETED(11, "Refund Completed"),
    REFUND_NOT_APPLICABLE(12, "Refund Not Applicable"),
    FAILED(13, "Process Failed"),
    CANCELLED(14, "Cancelled by User/System"),
    REFUND_FAILED(15, "Refund Failed");

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
        return this == SCHEDULED || this == PICKUP_CONFIRMED;
    }

    /**
     * Check if pickup confirmation is pending
     */
    public boolean isAwaitingConfirmation() {
        return this == SCHEDULED;
    }

    /**
     * Check if pickup can be initiated (confirmed status)
     */
    public boolean canInitiatePickup() {
        return this == PICKUP_CONFIRMED;
    }
}
