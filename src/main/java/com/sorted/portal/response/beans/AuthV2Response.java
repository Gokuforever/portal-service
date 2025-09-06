package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.UsersBean;
import lombok.Builder;

@Builder
public record AuthV2Response(
        @JsonProperty("user_info")
        UsersBean usersBean,
        @JsonProperty("redirection_url")
        String redirectionUrl
) {
}
