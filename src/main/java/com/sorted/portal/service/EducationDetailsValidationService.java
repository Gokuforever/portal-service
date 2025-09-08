package com.sorted.portal.service;

import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.beans.EducationCategoryField;
import com.sorted.commons.entity.mongo.EducationCategories;
import com.sorted.commons.entity.service.EducationCategoriesService;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.utils.Preconditions;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Log4j2
public class EducationDetailsValidationService {

    private final EducationCategoriesService educationCategoriesService;

    public void validate(EducationCategoryBean educationDetails) {
        Preconditions.check(educationDetails != null, ResponseCode.INVALID_REQ);
        Preconditions.check(StringUtils.hasText(educationDetails.getId()), ResponseCode.MANDATE_EDUCATION_ID);
        Preconditions.check(StringUtils.hasText(educationDetails.getEducation_level()), ResponseCode.MANDATE_EDUCATION_LEVEL);
        Preconditions.check(CollectionUtils.isNotEmpty(educationDetails.getFields()), ResponseCode.MANDATE_EDUCATION_LEVEL_DETAILS);

        log.debug("Processing education details for education ID: {}", educationDetails.getId());
        EducationCategories educationCategory = educationCategoriesService.findById(educationDetails.getId())
                .orElseThrow(() -> {
                    log.error("Education category not found for ID: {}", educationDetails.getId());
                    return new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
                });
        Map<String, EducationCategoryField> fieldsmap = educationDetails.getFields().stream().collect(Collectors.toMap(
                EducationCategoryField::getAlias, e -> e));


        for (EducationCategoryField field : educationCategory.getFields()) {
            boolean mandatory = field.isMandatory();
            boolean containsKey = fieldsmap.containsKey(field.getAlias());
            if (mandatory && !containsKey) {
                throw new CustomIllegalArgumentsException(ResponseCode.MISSING_MANDATE_EDUCATION_DETAILS);
            }
            EducationCategoryField educationCategoryField = fieldsmap.get(field.getAlias());
            List<String> options = educationCategoryField.getOptions();
            Preconditions.check(CollectionUtils.isNotEmpty(options), ResponseCode.MISSING_MANDATE_EDUCATION_DETAILS);
            options.remove("");
            Preconditions.check(CollectionUtils.isNotEmpty(options), ResponseCode.MISSING_MANDATE_EDUCATION_DETAILS);

            Preconditions.check(new HashSet<>(field.getOptions()).containsAll(options), ResponseCode.ERR_0001);
            if (options.contains("Other")) {
                Preconditions.check(StringUtils.hasText(educationCategoryField.getDescription()), ResponseCode.MISSING_DESCRIPTION);
            }
        }

//        for (EducationCategoryField field : educationDetails.getFields()) {
//            boolean containsKey = map.containsKey(field.getAlias());
//            boolean mandatory = field.isMandatory();
//            if (mandatory) {
//                Preconditions.check(containsKey, ResponseCode.MISSING_MANDATE_EDUCATION_DETAILS);
//            }
//            if (!containsKey)
//                continue;
//            List<String> options = map.get(field.getAlias());
//            Preconditions.check(CollectionUtils.isNotEmpty(options), ResponseCode.MISSING_MANDATE_EDUCATION_DETAILS);
//            Preconditions.check(new HashSet<>(field.getOptions()).containsAll(options), ResponseCode.ERR_0001);
//            if (options.contains("Other")) {
//                Preconditions.check(StringUtils.hasText(field.getDescription()), ResponseCode.MISSING_DESCRIPTION);
//            }
//        }
    }

}
