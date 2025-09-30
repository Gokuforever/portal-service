package com.sorted.portal.bl_services;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Feedback;
import com.sorted.commons.entity.service.FeedbackService;
import com.sorted.portal.request.beans.FeedbackBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ManageFeedback_BLService {

    private final FeedbackService feedbackService;

    @PostMapping("/feedback")
    public String addFeedback(@RequestBody FeedbackBean feedback, HttpServletRequest request) {

        String req_user_id = request.getHeader("req_user_id");

        Feedback build = Feedback.builder()
                .feedback(feedback.getFeedback())
                .userId(req_user_id)
                .build();

        feedbackService.create(build, Defaults.SYSTEM_ADMIN);
        return "Thanks for your feedback, we really appreciate it.";
    }
}
