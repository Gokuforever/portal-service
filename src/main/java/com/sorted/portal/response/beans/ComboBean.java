package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComboBean {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    @JsonProperty("creation_date")
    private String creationDate;

}
