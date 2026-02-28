package com.sorted.portal.bl_services;

import com.sorted.common.beans.RegisterRecommendationsBean;
import com.sorted.common.entity.mongo.RecommendersEntity;
import com.sorted.common.service.RecommendationsHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/recommendations")
@RestController
@RequiredArgsConstructor
public class ManageRecommendations_BLService {

    private final RecommendationsHandlerService recommendationsHandlerService;

    @PostMapping("/register")
    public void registerRecommendation(@RequestBody RegisterRecommendationsBean request) {
        recommendationsHandlerService.registerRecommendation(request);
    }

    @GetMapping("/search/recommender/{phoneNumber}")
    public RecommendersEntity searchRecommendation(@PathVariable String phoneNumber) {
        return recommendationsHandlerService.searchRecommender(phoneNumber);
    }

}
