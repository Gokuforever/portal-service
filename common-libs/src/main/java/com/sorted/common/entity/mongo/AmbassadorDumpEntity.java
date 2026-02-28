package com.sorted.common.entity.mongo;

import com.sorted.common.entity.beans.AmbassadorDetails;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "feedback")
@Builder
public class AmbassadorDumpEntity extends BaseMongoEntity<String> {

    private Boolean created;

    private AmbassadorDetails details;
}
