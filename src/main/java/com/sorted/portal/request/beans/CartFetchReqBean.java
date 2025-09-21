package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class CartFetchReqBean extends ReqBaseBean {

    private String address_id;
    @JsonProperty("coupon_code")
    private String couponCode;
}
