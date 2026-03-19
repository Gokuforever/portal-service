package com.sorted.common.entity.mongo;

import com.sorted.common.enums.NotifyRestockStatus;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "notify_restock")
@Builder
public class NotifyRestockEntity extends BaseMongoEntity<String> {
    @Field("product_master_id")
    private String productMasterId;
    @Field("zone_id")
    private String zoneId;
    @Field("user_id")
    private String userId;
    private NotifyRestockStatus status;
    private boolean read;
}
