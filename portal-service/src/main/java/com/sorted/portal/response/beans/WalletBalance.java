package com.sorted.portal.response.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record WalletBalance(
        BigDecimal balance,
        BigDecimal totalEarned,
        BigDecimal totalSpent,
        BigDecimal totalExpired
) {
}
