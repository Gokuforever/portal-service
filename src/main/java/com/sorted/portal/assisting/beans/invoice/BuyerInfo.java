package com.sorted.portal.assisting.beans.invoice;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BuyerInfo {
    private String name;
    private String email;
    private String address;
    private String gstNo; // Optional
}
