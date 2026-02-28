package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
public class SellerInfo {
    @JsonProperty("seller_id")
    @Field("seller_id")
    private String sellerId;
    private String name;
    private String address;
    @JsonProperty("phone_no")
    @Field("email_id")
    private String phoneNo;
    @JsonProperty("gst_no")
    @Field("gst_no")
    private String gstNo;
}
