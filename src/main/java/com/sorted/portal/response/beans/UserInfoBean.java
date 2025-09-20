package com.sorted.portal.response.beans;

import lombok.Builder;

@Builder
public record UserInfoBean(
        String id,
        String mobile,
        String name,
        String email
) {
}
