package com.sorted.portal.request.beans;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class InitiateSecureBean extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    private List<String> orderItemIds;
    @JsonProperty("return_date")
    private String returnDate;
}
