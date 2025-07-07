package com.sorted.portal.request.beans;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.enums.TimeSlot;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class InitiateSecureBean extends ReqBaseBean {
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("order_item_ids")
    private List<String> orderItemIds;
    @JsonProperty("return_date")
    private String returnDate;
    @JsonProperty("time_slot")
    private TimeSlot timeSlot;
    @JsonProperty("address_id")
    private String addressId;
}
