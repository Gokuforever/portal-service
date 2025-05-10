package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record AddressResponse(String street_1, String street_2, String landmark, String city, String state,
                              String pincode, @JsonProperty("address_type") String addressType,
                              @JsonProperty("address_type_desc") String addressTypeDesc, String phone_no) {
}
