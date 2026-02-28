package com.sorted.common.beans;

import lombok.Builder;

import java.util.List;

@Builder
public record ReferralDetails(
        String name,
        String mobileNo,
        List<String> referredUsers,
        int count
) {
}
