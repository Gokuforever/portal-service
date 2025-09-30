package com.sorted.portal.response.beans;

import lombok.Builder;

@Builder
public record AssetDetails(
        String id,
        String url,
        int order,
        boolean mobileView
) {
}
