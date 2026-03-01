package com.sorted.portal.request.beans;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.TimeSlot;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class InitiateSecureBean extends ReqBaseBean {
    @JsonProperty("secure_return_id")
    private String secureReturnId;
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("return_date")
    private String returnDate;
    @JsonProperty("time_slot")
    private TimeSlot timeSlot;
    @JsonProperty("address_id")
    private String addressId;
}
