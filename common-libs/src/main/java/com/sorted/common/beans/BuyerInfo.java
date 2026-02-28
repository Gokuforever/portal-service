package com.sorted.common.beans;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
public class BuyerInfo {
    private String name;
    private String email;
    private String address;
    @Field("gst_no")
    private String gstNo; // Optional
}

