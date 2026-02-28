package com.sorted.portal.request.beans;

import com.sorted.common.helper.ReqBaseBean;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AddReview extends ReqBaseBean {
    private String productId;
    private String title;
    private String review;
    private int rating;
}
