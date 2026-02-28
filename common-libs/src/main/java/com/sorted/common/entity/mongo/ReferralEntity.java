package com.sorted.common.entity.mongo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "referral")
@Builder
public class ReferralEntity extends BaseMongoEntity<String> {

    private String userId;
    private String code;
    private List<String> users;
    private Integer count;
    private boolean active;
}
