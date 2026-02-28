package com.sorted.common.beans;

import lombok.Builder;

@Builder
public record PromoBanners(
        String url,
        int order,
        String altText,
        boolean mobileView
) {
}
