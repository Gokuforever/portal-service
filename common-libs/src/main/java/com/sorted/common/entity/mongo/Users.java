package com.sorted.common.entity.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.sorted.common.beans.EducationCategoryBean;
import com.sorted.common.enums.Gender;
import com.sorted.common.utils.GsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "users")
public class Users extends BaseMongoEntity<String> {

    private static final String EDUCATION_DETAILS = "education_details";
    private static final String NEAREST_SELLER = "nearest_seller";
    private static final String NEAREST_PINCODE = "nearest_pincode";

    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;
    private String code;
    private String first_name;
    private String last_name;
    private String mobile_no;
    private String email_id;
    private String password;
    private String old_password;
    private String profile_picture_id;
    private String role_id;
    private Integer status;
    private String semester;
    private String branch;
    private String college;
    private String branch_desc;
    private Boolean is_verified = false;
    private LocalDateTime reset_pass_request_expiry;
    private String uuid;
    private boolean pass_changed;
    private Gender gender;
    private Map<String, String> properties;
    private boolean ambassador;
    @Field("ambassador_id")
    private String ambassadorId;
    private String nearestZoneId;
    private BigDecimal currentLat;
    private BigDecimal currentLng;

    @JsonIgnore
    public void setEducationDetails(EducationCategoryBean educationCategoryBean) {
        Gson gson = GsonUtils.getGson();
        String json = gson.toJson(educationCategoryBean);
        this.setProperty(EDUCATION_DETAILS, json);
    }

    @JsonIgnore
    public void setNearestSeller(@NonNull String nearestSeller) {
        this.setProperty(NEAREST_SELLER, nearestSeller);
    }

    @JsonIgnore
    public String getNearestSeller() {
        return this.getProperty(NEAREST_SELLER);
    }

    @JsonIgnore
    public void setNearestPincode(@NonNull String nearestPincode) {
        this.setProperty(NEAREST_PINCODE, nearestPincode);
    }

    @JsonIgnore
    public String getNearestPincode() {
        return this.getProperty(NEAREST_PINCODE);
    }

    @JsonIgnore
    private void setProperty(String key, String json) {
        if (this.properties == null) {
            this.properties = new java.util.HashMap<>();
        }
        this.properties.put(key, json);
    }

    @JsonIgnore
    public EducationCategoryBean getEducationDetails() {
        String json = getProperty(EDUCATION_DETAILS);
        if (json == null) return null;
        Gson gson = GsonUtils.getGson();
        return gson.fromJson(json, EducationCategoryBean.class);
    }

    @JsonIgnore
    private String getProperty(String key) {
        if (this.properties == null) {
            return null;
        }
        return this.properties.getOrDefault(key, null);
    }

}
