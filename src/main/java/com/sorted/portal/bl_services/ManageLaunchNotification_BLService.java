package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.LaunchingEmail;
import com.sorted.commons.entity.service.LaunchingEmailService;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.utils.Preconditions;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.NotifyOnLaunch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sorted.commons.constants.Defaults.SYSTEM_ADMIN;

@RestController
public class ManageLaunchNotification_BLService {

    @Autowired
    private LaunchingEmailService launchingEmailService;



    @PostMapping("/notify")
    public void notification(@RequestBody NotifyOnLaunch request) {
        Preconditions.check(request != null, ResponseCode.INVALID_REQ);
        Preconditions.check(StringUtils.hasText(request.getEmail()), ResponseCode.INVALID_REQ);
        LaunchingEmail launchingEmail = LaunchingEmail.builder()
                .mail(request.getEmail())
                .sent(false)
                .build();
        launchingEmailService.create(launchingEmail, SYSTEM_ADMIN);
    }

    @PostMapping("/launch")
    public void launch() {
        List<LaunchingEmail> launchingEmails = launchingEmailService.repoFindAll();
        if (launchingEmails.isEmpty()) {
            return;
        }
        Set<String> emailIds = launchingEmails.stream().map(LaunchingEmail::getMail).collect(Collectors.toSet());
        for (LaunchingEmail launchingEmail : launchingEmails) {

        }
    }

}
