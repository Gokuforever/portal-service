package com.sorted.common.entity.mongo;

import com.sorted.common.enums.AssetType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "assets")
public class AssetsEntity extends BaseMongoEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    private AssetType type;
    private String url;
    private int order;
    @Field("alt_text")
    private String altText;
    @Field("mobile_view")
    private boolean mobileView;
}
