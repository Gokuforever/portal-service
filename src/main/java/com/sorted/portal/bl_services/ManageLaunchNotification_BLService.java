package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.LaunchingEmail;
import com.sorted.commons.entity.service.LaunchingEmailService;
import com.sorted.commons.enums.MailTemplate;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.NotifyOnLaunch;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sorted.commons.constants.Defaults.SYSTEM_ADMIN;

@RestController
@RequiredArgsConstructor
public class ManageLaunchNotification_BLService {

    private final LaunchingEmailService launchingEmailService;
    private final EmailSenderImpl emailSenderImpl;

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
        List<LaunchingEmail> list = launchingEmails.stream().filter(e -> !e.isSent()).toList();
        if (list.isEmpty()) {
            return;
        }
        for (LaunchingEmail email : list) {
            MailBuilder mailBuilder = new MailBuilder();
            mailBuilder.setTo(email.getMail());
            mailBuilder.setSubject(MailTemplate.LAUNCHING_MAIL.getSubject());
            mailBuilder.setTemplate(MailTemplate.LAUNCHING_MAIL);
            emailSenderImpl.sendEmailHtmlTemplate(mailBuilder);
            email.setSent(true);
            launchingEmailService.update(email.getId(), email, SYSTEM_ADMIN);
        }
    }
}
