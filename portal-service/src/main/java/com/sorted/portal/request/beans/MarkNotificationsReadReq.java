package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class MarkNotificationsReadReq extends ReqBaseBean {
    @JsonProperty("notification_ids")
    private List<String> notificationIds;
}
