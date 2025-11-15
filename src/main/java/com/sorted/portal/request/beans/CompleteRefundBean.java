package com.sorted.portal.request.beans;

import lombok.Builder;

@Builder
public record CompleteRefundBean(
        String refundId,
        String orderId,
        int statusId,
        String userName
) {
}
