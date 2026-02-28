package com.sorted.common.entity.mongo;

import com.sorted.common.beans.GroupComponent;
import com.sorted.common.beans.ProductCarousel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "home_config")
@Builder
public class HomeConfig extends BaseMongoEntity<String> {

    @Field("category_id")
    private String categoryId;
    @Field("main_badge")
    private String mainBadge;
    @Field("main_title")
    private String mainTitle;
    @Field("main_subtitle")
    private String mainSubtitle;
    @Field("product_carousel")
    private ProductCarousel productCarousel;
    @Field("group_component")
    private List<GroupComponent> groupComponent;
}
