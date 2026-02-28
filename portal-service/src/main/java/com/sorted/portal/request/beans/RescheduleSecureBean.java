package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.TimeSlot;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request bean for rescheduling a secure return pickup
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class RescheduleSecureBean extends ReqBaseBean {
    
    @JsonProperty("order_id")
    private String orderId;
    
    @JsonProperty("new_pickup_date")
    private String newPickupDate;
    
    @JsonProperty("new_time_slot")
    private TimeSlot newTimeSlot;
    
    @JsonProperty("address_id")
    private String addressId; // Optional - if customer wants to change pickup address
}
