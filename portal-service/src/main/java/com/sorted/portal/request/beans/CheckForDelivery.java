package com.sorted.portal.request.beans;

import java.math.BigDecimal;

public record CheckForDelivery(
        BigDecimal lat,
        BigDecimal lng
) {
}
