package com.sorted.portal.bl_services;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.HomeConfig;
import com.sorted.commons.entity.mongo.HomeConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ManageHomeConfig_BLService {

    private final HomeConfigService homeConfigService;

    @PostMapping("/homeConfig")
    public void addHomeConfig(@RequestBody HomeConfig homeConfig) {
        homeConfigService.create(homeConfig, Defaults.SYSTEM_ADMIN);
    }
}
