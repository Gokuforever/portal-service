package com.sorted.portal.assisting.beans.config;

import lombok.Builder;

@Builder
public record PromoBanners(
        String url,
        int order,
        String altText
) {
}
