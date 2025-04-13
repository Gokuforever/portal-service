package com.sorted.portal.response.beans;

import java.math.BigDecimal;

public record SettlementAnalyticsResponse(BigDecimal paid, BigDecimal unpaid) {
}
