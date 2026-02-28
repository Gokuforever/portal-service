package com.sorted.common.notifications;

import com.sorted.common.enums.SmsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SMSService {

    @Value("${fast2sms.auth.token}")
    private String sms_auth_token;


    private final List<String> internalPhones = List.of("9867292392", "9156015331", "9004180031");
    private final List<SmsTemplate> notifyToInternalTeam = List.of(SmsTemplate.NEW_ORDER, SmsTemplate.ORDER_CONFIRMED);
    private final RestTemplate restTemplate = new RestTemplate();

    public String sendSMS(List<String> mobileNumbers, String content, SmsTemplate template) {

        if (notifyToInternalTeam.contains(template)) {
            List<String> newMobileNumbers = new ArrayList<>();
            newMobileNumbers.addAll(mobileNumbers);
            newMobileNumbers.addAll(internalPhones);
            mobileNumbers = new ArrayList<>(newMobileNumbers);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", sms_auth_token);

        String numbers = String.join(",", mobileNumbers);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("sender_id", "STDZ");
        formData.add("message", template.getTemplateId());
        formData.add("entity_id", "1201175208011209565");
        formData.add("route", "dlt");
        formData.add("numbers", numbers);
        formData.add("variables_values", content);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        return restTemplate.postForObject("https://www.fast2sms.com/dev/bulkV2", request, String.class);
    }

    public static void main(String[] args) {
        List<String> mobileNumbers = List.of("9867292392");
        List<String> internalPhones = List.of("9920534134");
        List<String> newMobileNumbers = new ArrayList<>();
        newMobileNumbers.addAll(mobileNumbers);
        newMobileNumbers.addAll(internalPhones);
        mobileNumbers = new ArrayList<>(newMobileNumbers);
        System.out.println(String.join(",", mobileNumbers));
    }
}
