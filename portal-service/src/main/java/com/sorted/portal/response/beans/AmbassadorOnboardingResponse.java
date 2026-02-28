package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.ProcessType;
import lombok.Builder;

@Builder
public record AmbassadorOnboardingResponse(
        @JsonProperty("process_type") ProcessType processType,
        @JsonProperty("entity_id") String entityId,
        @JsonProperty("reference_id") String referenceId
) {
}
