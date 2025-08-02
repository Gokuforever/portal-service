package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Category_Master;
import lombok.*;

import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Config {
    private List<Category_Master> categories;
}
