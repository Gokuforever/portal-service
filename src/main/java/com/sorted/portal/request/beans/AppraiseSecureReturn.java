package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = false)
public class AppraiseSecureReturn extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    private BigDecimal amount;
    private String remark;
}
