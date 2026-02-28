package com.sorted.common.notifications;

import com.sorted.common.enums.MailTemplate;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.MailBuilder;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Component
public class EmailSenderImpl {

    @Value("${spring.mail.username}")
    private String sender_mail;

    @Value("${se.email.template.base_folder}")
    private String email_base_folder;

    @Value("${spring.profiles.active}")
    private String profile;

    private final JavaMailSender mailSender;

    public EmailSenderImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendEmailHtmlTemplate(MailBuilder builder) {

        if (CollectionUtils.isEmpty(builder.getTo())) {
            throw new CustomIllegalArgumentsException(ResponseCode.RECIPIENT_MISSING);
        }

        MailTemplate template = builder.getTemplate();
        if (template == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.TEMPLATE_IS_MISSING);
        }

        String content = builder.getContent();
        String file_name = template.getFile_name();
        String str_template = loadTemplate(email_base_folder + file_name);
        if (str_template == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        str_template = StringUtils.hasText(content) ? this.replacePlaceholders(str_template, content) : str_template;

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            mimeMessageHelper.setTo(builder.getTo().toArray(new String[0]));

            if (!CollectionUtils.isEmpty(builder.getCc())) {
                mimeMessageHelper.setCc(builder.getCc().toArray(new String[0]));
            }

            if (!CollectionUtils.isEmpty(builder.getBcc())) {
                mimeMessageHelper.setBcc(builder.getBcc().toArray(new String[0]));
            }

            mimeMessageHelper.setSubject(template.getSubject());
            mimeMessageHelper.setFrom(sender_mail);
            mimeMessageHelper.setText(str_template, true);

            // Add CloudFront URL attachments if present
            if (!CollectionUtils.isEmpty(builder.getAttachmentUrls())) {
                for (String cloudFrontUrl : builder.getAttachmentUrls()) {
                    addAttachmentFromUrl(mimeMessageHelper, cloudFrontUrl);
                }
            }

            mailSender.send(mimeMessage);
            log.info("Email sent successfully");
        } catch (Exception e) {
            log.error("Error sending email", e);
            e.printStackTrace();
        }
    }

    /**
     * Downloads file from CloudFront URL and adds it as attachment
     *
     * @param helper  MimeMessageHelper instance
     * @param fileUrl CloudFront URL of the file
     * @throws Exception if download or attachment fails
     */
    private void addAttachmentFromUrl(MimeMessageHelper helper, String fileUrl) throws Exception {
        try {
            log.info("Downloading attachment from URL: {}", fileUrl);

            // Download file from URL
            URL url = new URL(fileUrl);
            byte[] fileData = downloadFileFromUrl(url);

            // Extract filename from URL or use default
            String fileName = extractFileNameFromUrl(fileUrl);

            // Create InputStreamSource from byte array
            InputStreamSource attachmentSource = new ByteArrayResource(fileData);

            // Add attachment
            helper.addAttachment(fileName, attachmentSource);

            log.info("Successfully added attachment: {}", fileName);

        } catch (Exception e) {
            log.error("Failed to add attachment from URL: {}", fileUrl, e);
            throw e;
        }
    }

    /**
     * Downloads file content from URL
     *
     * @param url URL to download from
     * @return byte array of file content
     * @throws IOException if download fails
     */
    private byte[] downloadFileFromUrl(URL url) throws IOException {
        try (InputStream inputStream = url.openStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toByteArray();
        }
    }

    /**
     * Extracts filename from URL
     *
     * @param url CloudFront URL
     * @return filename or default name
     */
    private String extractFileNameFromUrl(String url) {
        try {
            String path = new URL(url).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);

            // If no filename found or empty, use default
            if (fileName.isEmpty()) {
                fileName = "attachment_" + System.currentTimeMillis();
            }

            return fileName;
        } catch (Exception e) {
            log.warn("Could not extract filename from URL: {}, using default", url);
            return "attachment_" + System.currentTimeMillis();
        }
    }

    private String loadTemplate(String templateFilePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(templateFilePath)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String replacePlaceholders(String str_template, String content) {
        String[] values = content.split("\\|");
        for (int i = 0; i < values.length; i++) {
            str_template = str_template.replace("{{a" + i + "}}", values[i]);
        }
        return str_template;
    }
}