package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Appraisal grade for returned items in secure buy/return process.
 * Determines the refund percentage based on item condition.
 */
@Getter
@AllArgsConstructor
public enum AppraisalGrade {

    A("Grade A - Excellent Condition", 50.0),  // 50% refund
    B("Grade B - Good Condition", 30.0),       // 30% refund
    C("Grade C - Poor/Damaged", 0.0);          // 0% refund (rejected)

    private final String description;
    private final Double refundPercentage;

    /**
     * Calculate refund amount based on the grade and selling price after discount.
     *
     * @param sellingPriceAfterDiscount The selling price after discount
     * @return Calculated refund amount
     */
    public Long calculateRefund(Long sellingPriceAfterDiscount) {
        if (sellingPriceAfterDiscount == null || sellingPriceAfterDiscount <= 0) {
            return 0L;
        }
        return (long) (sellingPriceAfterDiscount * refundPercentage / 100);
    }

    /**
     * Get refund percentage as integer (for display purposes)
     */
    public Integer getRefundPercentageInt() {
        return refundPercentage.intValue();
    }
}
