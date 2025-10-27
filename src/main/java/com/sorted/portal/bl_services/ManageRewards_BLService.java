package com.sorted.portal.bl_services;

import com.sorted.commons.beans.CreateRewardRuleBean;
import com.sorted.commons.helper.RewardsUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rewards")
public class ManageRewards_BLService {

    private final RewardsUtility rewardsUtility;

    @PostMapping("/create")
    public void createRewards(@RequestBody CreateRewardRuleBean request) {
        rewardsUtility.createRewardRule(request);
    }

}
