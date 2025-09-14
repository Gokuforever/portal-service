package com.sorted.portal.bl_services;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/address")
public class ManageAddressV2_BLService {

    @PostMapping("/add")
    public void addAddress() {

    }
}
