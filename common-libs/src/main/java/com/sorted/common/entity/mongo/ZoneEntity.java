package com.sorted.common.entity.mongo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document("zone")
public class ZoneEntity extends BaseMongoEntity<String> {

    private String name;
    @Field("zone_id")
    private String zoneId;
    private GeoJsonPolygon geometry;

}
