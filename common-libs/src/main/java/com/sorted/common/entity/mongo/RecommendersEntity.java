package com.sorted.common.entity.mongo;

import com.sorted.common.entity.beans.Consent;
import com.sorted.common.entity.beans.IndustryExpertInfo;
import com.sorted.common.entity.beans.ProfessorInfo;
import com.sorted.common.entity.beans.StudentInfo;
import com.sorted.common.enums.RecommenderType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "recommenders")
@Builder
public class RecommendersEntity extends BaseMongoEntity<String> {
    @Field("phone_no")
    private String phoneNo;
    private RecommenderType type;
    @Field("first_name")
    private String firstName;
    @Field("last_name")
    private String lastName;
    private String email;
    @Field("photo_url")
    private String photoUrl;
    private String bio;
    private Consent consent;
    @Field("professor_info")
    private ProfessorInfo professorInfo;
    @Field("student_info")
    private StudentInfo studentInfo;
    @Field("industry_expert_info")
    private IndustryExpertInfo industryExpertInfo;

}
