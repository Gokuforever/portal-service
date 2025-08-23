package com.sorted.portal.assisting.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record ProductFindOneResBean(ProductDetailsBeanList product,
                                    @JsonProperty("related_products") List<ProductDetailsBeanList> relatedProducts) {
}
