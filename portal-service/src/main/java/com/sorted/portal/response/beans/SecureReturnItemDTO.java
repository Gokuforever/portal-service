package com.sorted.portal.response.beans;

import com.sorted.common.enums.AppraisalGrade;
import com.sorted.common.enums.SecureItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Secure Return Item response.
 * Represents individual items in a secure return.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureReturnItemDTO {

    // Product Info
    private String order_item_id;
    private String product_id;
    private String product_code;
    private String product_name;
    private String product_image_url;

    // Quantity & Pricing
    private Long quantity;
    private Long selling_price_after_discount;
    private Long total_item_cost;

    // Appraisal
    private AppraisalGrade appraisal_grade;
    private String appraisal_grade_description;
    private String appraisal_remarks;
    private List<String> returned_item_image_urls;
    private LocalDateTime appraised_at;
    private String appraised_by;

    // Refund
    private Long estimated_refund_amount;
    private Long actual_refund_amount;
    private Double refund_percentage;

    // Status
    private SecureItemStatus item_status;
    private String item_status_description;
}
