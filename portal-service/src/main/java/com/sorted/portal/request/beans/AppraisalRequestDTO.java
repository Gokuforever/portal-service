package com.sorted.portal.request.beans;

import com.sorted.common.enums.AppraisalGrade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for item appraisal by seller.
 * Contains appraisal details for individual items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppraisalRequestDTO {

    private String secure_return_id;
    private String req_user_id;  // Seller ID
    private List<ItemAppraisal> item_appraisals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemAppraisal {
        private String order_item_id;
        private AppraisalGrade grade;  // A, B, or C
        private String remarks;
        private List<String> image_urls;  // Photos of returned items
    }
}
