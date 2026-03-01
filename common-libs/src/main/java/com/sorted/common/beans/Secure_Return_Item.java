package com.sorted.common.beans;

import com.sorted.common.enums.AppraisalGrade;
import com.sorted.common.enums.SecureItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents an individual item in a secure return.
 * Embedded within Secure_Return entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Secure_Return_Item implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Product Reference
    private String order_item_id;            // Link to Order_Item
    private String product_id;
    private String product_code;
    private String product_name;
    private String product_image_url;        // Original product image

    // Quantity & Pricing
    private Long quantity;
    private Long selling_price_after_discount;  // Per unit price
    private Long total_item_cost;            // quantity * selling_price_after_discount

    // Appraisal (Seller's Review)
    private AppraisalGrade appraisal_grade;  // A, B, or C
    private String appraisal_remarks;        // Seller's detailed comments
    private List<String> returned_item_image_urls;  // Photos of returned items
    private LocalDateTime appraised_at;
    private String appraised_by;             // Seller user ID

    // Refund Calculation
    private Long estimated_refund_amount;    // 50% of total_item_cost (optimistic)
    private Long actual_refund_amount;       // Based on appraisal_grade
    private Double refund_percentage;        // 50%, 30%, or 0%

    // Item Status
    private SecureItemStatus item_status;

    /**
     * Calculate and set refund based on appraisal grade
     */
    public void applyAppraisal(AppraisalGrade grade, String remarks, String appraisedBy, List<String> imageUrls) {
        this.returned_item_image_urls = imageUrls;
        this.appraisal_grade = grade;
        this.appraisal_remarks = remarks;
        this.appraised_by = appraisedBy;
        this.appraised_at = LocalDateTime.now();
        this.refund_percentage = grade.getRefundPercentage();
        this.actual_refund_amount = grade.calculateRefund(this.total_item_cost);
        this.item_status = SecureItemStatus.fromAppraisalGrade(grade);
    }

    /**
     * Initialize estimated refund (assumes Grade A - 50%)
     */
    public void calculateEstimatedRefund() {
        if (this.total_item_cost != null) {
            this.estimated_refund_amount = AppraisalGrade.A.calculateRefund(this.total_item_cost);
        }
    }
}
