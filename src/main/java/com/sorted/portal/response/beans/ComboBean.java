package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.portal.enums.ComboStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;


@Builder
public record ComboBean(
        String id,
        String name,
        String description,
        BigDecimal price,
        @JsonProperty("creation_date")
        String creationDate,
        @JsonProperty("combo_status")
        ComboStatus comboStatus,
        List<ComboProduct> products
) {

}
