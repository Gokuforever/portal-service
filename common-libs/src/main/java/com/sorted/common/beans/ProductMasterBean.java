package com.sorted.common.beans;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Builder
public record ProductMasterBean(
        String id,
        String catagory_id,
        String name,
        String img_src,
        Integer group_id,
        String group_name,
        Map<String, List<String>> sub_categories,
        String cdn_url,
        BigDecimal mrp,
        String desc,
        // BaseMongoEntity fields
        String created_by,
        String modified_by,
        LocalDateTime creation_date,
        LocalDateTime modification_date,
        String creation_date_str,
        String modification_date_str,
        boolean deleted
) {
}
