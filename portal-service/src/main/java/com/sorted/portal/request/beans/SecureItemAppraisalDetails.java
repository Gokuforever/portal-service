package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.AppraisalGrade;

import java.util.List;

public record SecureItemAppraisalDetails(
        @JsonProperty("order_item_id")
        String orderItemId,
        AppraisalGrade grade,
        String remarks,
        List<String> imageUrls
) {
}
