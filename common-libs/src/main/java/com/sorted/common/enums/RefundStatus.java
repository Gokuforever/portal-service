package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Refund status for secure return process.
 * Tracks the payment gateway refund lifecycle.
 */
@Getter
@AllArgsConstructor
public enum RefundStatus {

    NOT_INITIATED("Refund Not Started"),
    PENDING("Refund Initiated"),
    NOT_APPLICABLE("Refund Not Applicable"),
    PROCESSING("Payment Gateway Processing"),
    COMPLETED("Refund Completed"),
    FAILED("Refund Failed"),
    PARTIAL("Partial Refund Completed");

    private final String description;

    /**
     * Check if refund is in a terminal state
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == PARTIAL;
    }

    /**
     * Check if refund can be retried
     */
    public boolean canRetry() {
        return this == FAILED;
    }
}
