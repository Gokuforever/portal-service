package com.sorted.common.entity.mongo;

import com.sorted.common.entity.beans.DisplaySettings;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "recommendations")
@Builder
public class RecommendationsEntity extends BaseMongoEntity<String> {

    @Field("product_id")
    private String productId;
    private String title;
    private String text;
    private int rating;
    @Field("display_settings")
    private DisplaySettings displaySettings;
    @Field("recommender_id")
    private String recommenderId;
}
