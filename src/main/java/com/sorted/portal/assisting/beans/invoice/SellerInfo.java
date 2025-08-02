package com.sorted.portal.assisting.beans.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SellerInfo {
    @JsonProperty("seller_id")
    private String sellerId;
    private String name;
    private String address;
    @JsonProperty("phone_no")
    private String phoneNo;
    @JsonProperty("gst_no")
    private String gstNo;

}
