package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record Assets(
        @JsonProperty("home_promo_banners")
        List<PromoBanners> homePromoBanners
) {
}
