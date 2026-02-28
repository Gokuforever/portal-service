package com.sorted.common.utils;

import com.sorted.common.enums.MailTemplate;
import com.sorted.common.helper.MailBuilder;
import com.sorted.common.notifications.EmailSenderImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalMailService {

    private final List<String> internalEmails = Arrays.asList("support@studeaze.in", "yogeshkhaire288@gmail.com", "anandsuryawanshi66@gmail.com", "vinayakjopre@gmail.com");
    private final EmailSenderImpl emailSenderImpl;

    public void sendMailOnError(String summary, String subject) {
        this.sendMailOnError(summary, subject, null);
    }

    public void sendMailOnError(String summary, String subject, Exception e) {
        String errorContent = e == null ? String.format("%s|%s", summary, LocalDateTime.now()) : String.format("%s|%s|%s", summary, LocalDateTime.now(), e.getMessage());
        MailBuilder builder = new MailBuilder();
        builder.setTo(internalEmails);
        builder.setContent(errorContent);
        builder.setTemplate(MailTemplate.ERROR);
        builder.setSubject(subject);
        emailSenderImpl.sendEmailHtmlTemplate(builder);
    }
}
