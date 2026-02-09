package com.sorted.portal.bl_services;

import com.sorted.commons.beans.CreateZoneRequest;
import com.sorted.commons.service.ZoneHandlerService;
import com.sorted.portal.request.beans.AssignZoneRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/zone")
public class ManageZones_BLService {

    private final ZoneHandlerService zoneHandlerService;

    @PostMapping("/create")
    public void create(@RequestBody CreateZoneRequest request){
        zoneHandlerService.createZone(request);
    }

    @PostMapping("/assign")
    public void assign(@RequestBody AssignZoneRequest request){
        zoneHandlerService.assignZone(request.sellerId(), request.zoneId());
    }
}
