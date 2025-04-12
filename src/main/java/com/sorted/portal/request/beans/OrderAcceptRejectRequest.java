package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderAcceptRejectRequest extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    private boolean accepted;
    private String remark;
}
