package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.helper.ReqBaseBean;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAcceptRejectRequest extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("accepted_items")
    private List<String> acceptedItemIds;
    @JsonProperty("rejection_reason")
    private String rejectionReason;
    private boolean accepted;
    private String remark;

    public boolean isPartialAccept() {
        return acceptedItemIds != null && !acceptedItemIds.isEmpty();
    }
}
