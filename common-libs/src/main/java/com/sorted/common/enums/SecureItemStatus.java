package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Item-level status for secure return items.
 * Tracks individual item appraisal and refund status.
 */
@Getter
@AllArgsConstructor
public enum SecureItemStatus {

    PENDING_APPRAISAL("Awaiting Seller Review"),
    APPROVED_GRADE_A("Approved - Grade A (50% refund)"),
    APPROVED_GRADE_B("Approved - Grade B (30% refund)"),
    REJECTED_GRADE_C("Rejected - Grade C (No refund)"),
    REFUND_PROCESSED("Refund Processed");

    private final String description;

    /**
     * Get status based on appraisal grade
     */
    public static SecureItemStatus fromAppraisalGrade(AppraisalGrade grade) {
        return switch (grade) {
            case A -> APPROVED_GRADE_A;
            case B -> APPROVED_GRADE_B;
            case C -> REJECTED_GRADE_C;
        };
    }

    /**
     * Check if item has been appraised
     */
    public boolean isAppraised() {
        return this != PENDING_APPRAISAL;
    }
}
