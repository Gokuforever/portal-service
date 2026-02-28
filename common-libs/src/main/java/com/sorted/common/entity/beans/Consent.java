package com.sorted.common.entity.beans;

import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Builder
public class Consent {

    @Field("public_display_approved")
    private Boolean publicDisplayApproved;
    @Field("photo_usage_approved")
    private Boolean photoUsageApproved;
    @Field("marketing_usage_approved")
    private Boolean marketingUsageApproved;
    @Field("consent_date")
    private LocalDateTime consentDate;
    @Field("consent_doc_url")
    private String  consentDocUrl;
}
