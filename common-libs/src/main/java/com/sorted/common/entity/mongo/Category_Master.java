package com.sorted.common.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.enums.InputType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.CollectionUtils;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "category_master_new")
public class Category_Master extends BaseMongoEntity<String> {

    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;
    private String name;
    private String category_code;
    private boolean secure_item;
    private List<Groups> groups;


    @JsonIgnore
    public Map<Integer, List<SubCategory>> getSub_categories_by_group() {
        if (CollectionUtils.isEmpty(this.groups)) {
            return new HashMap<>();
        }
        return this.groups.stream().collect(Collectors.toMap(Groups::getGroup_id, Groups::getSub_categories));
    }

    @Data
    public static class Groups {
        private String group_name;
        private Integer group_id;
        private Integer group_order;
        private List<SubCategory> sub_categories;

    }

    @Data
    public static class SubCategory {
        private String name;
        private List<String> attributes;
        private boolean mandate;
        private int order;
        private InputType input_type;
        private TypescriptDataTypes data_type;
        private boolean filterable;
        private boolean mappable;
        private boolean related_filterable;
        private Map<String, List<String>> mapping;

        @JsonIgnore
        public List<String> getMappedAttributes() {
            if (this.mappable) {
                if (this.mapping != null) {
                    return new ArrayList<>(this.mapping.keySet());
                }
                return null;
            }
            return this.attributes;
        }
    }

    @Getter
    @AllArgsConstructor
    public enum TypescriptDataTypes {
        String, Number;
    }
}
