package com.sorted.common.beans;

import lombok.Builder;

@Builder
public record CouponCodeInfo(boolean isValid, boolean isFreeDelivery, long discountAmount) {

}
