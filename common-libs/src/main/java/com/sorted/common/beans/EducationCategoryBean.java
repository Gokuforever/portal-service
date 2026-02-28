package com.sorted.common.beans;

import com.sorted.common.entity.mongo.EducationCategories;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EducationCategoryBean {

    private String id;
    private String education_level;
    private List<EducationCategoryField> fields;

    public EducationCategoryBean(EducationCategories educationCategories) {
        this.education_level = educationCategories.getEducation_level();
        this.fields = educationCategories.getFields();
        this.id = educationCategories.getId();
    }
}
