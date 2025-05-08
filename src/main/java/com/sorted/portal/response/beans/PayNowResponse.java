package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record PayNowResponse(@JsonProperty("redirect_url") String redirectUrl,
                          @JsonProperty("order_id") String orderId) {

}