package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Media;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "combo")
public class Combo extends BaseMongoEntity<String> {
    private String name;
    private String code;
    private List<String> item_ids;
    private String description;
    private List<Media> media;
    private Long selling_price;
    private Long mrp;
    private boolean active;
    private String seller_id;
    private String seller_code;
}
