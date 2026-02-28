package com.sorted.common.beans;

import java.math.BigDecimal;

public record FeeResult(BigDecimal revenue, BigDecimal cost, Long revenueInPaise, Long costInPaise) {

    @Override
    public String toString() {
        return "Revenue (fee): " + revenue + " paise, Cost (actual): " + cost + " paise";
    }
}
