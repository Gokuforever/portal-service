package com.sorted.common.manage.otp;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Otp;
import com.sorted.common.entity.service.Otp_Service;
import com.sorted.common.enums.ProcessType;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.enums.SmsTemplate;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.*;
import com.sorted.common.notifications.SMSService;
import com.sorted.common.notifications.helper.SmsTraceHelper;
import com.sorted.common.utils.CommonUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManageOtp {

    private final Otp_Service otp_Service;

    @Value("${se.portal.otp_length}")
    private int otp_length;

    @Value("${fast2sms.auth.token}")
    private String sms_auth_token;

    @Value("${se.enable.sms:false}")
    private boolean enableSms;

    private final SmsTraceHelper smsTraceHelper;
    private final SMSService smsService;

    public String generateAndSaveOtp(String mobile_number, ProcessType process_type, String cud_by) {
        SEFilter filterO = new SEFilter(SEFilterType.AND);
        filterO.addClause(WhereClause.eq(Otp.Fields.mobile_no, mobile_number));
        filterO.addClause(WhereClause.eq(Otp.Fields.status, true));
        filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Otp> listOtp = otp_Service.repoFind(filterO);
        if (!CollectionUtils.isEmpty(listOtp)) {
            for (Otp tempOtp : listOtp) {
                tempOtp.setStatus(false);
                otp_Service.update(tempOtp.getId(), tempOtp, cud_by);
            }
        }

        Otp otp = new Otp();
        String random_otp;
        if (enableSms) {
            random_otp = CommonUtils.generateFixedLengthRandomNumber(otp_length);
        } else {
            random_otp = "1111";
        }
        otp.setOtp_value(random_otp);
        otp.setStatus(true);
        otp.setExpiry_at(LocalDateTime.now().plusMinutes(5));
        otp.setMobile_no(mobile_number);
        otp.setProcess_type(process_type);
        otp.setIs_verified(false);

        otp = otp_Service.create(otp, cud_by);
        this.sendSMS(mobile_number, random_otp, cud_by);
        return otp.getUuid();
    }

    public void verify(String mobileNo, @NonNull String uuid, @NonNull String otp, @NonNull ProcessType processType, String cud_by) {
        SEFilter filterO = new SEFilter(SEFilterType.AND);
        filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterO.addClause(WhereClause.eq(Otp.Fields.status, true));
        filterO.addClause(WhereClause.eq(Otp.Fields.is_verified, false));
        filterO.addClause(WhereClause.eq(Otp.Fields.otp_value, otp));
        filterO.addClause(WhereClause.eq(Otp.Fields.mobile_no, mobileNo));
        filterO.addClause(WhereClause.eq(Otp.Fields.uuid, uuid));
        filterO.addClause(WhereClause.eq(Otp.Fields.process_type, processType.name()));

        OrderBy orderBy = new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);
        filterO.setOrderBy(orderBy);

        Otp otp2 = otp_Service.repoFindOne(filterO);
        if (otp2 == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_OTP);
        }
        LocalDateTime now = LocalDateTime.now();
        if (otp2.getExpiry_at().isBefore(LocalDateTime.now())) {
            throw new CustomIllegalArgumentsException(ResponseCode.OTP_EXPIRED);
        }
        otp2.setIs_verified(true);
        otp2.setVerified_at(now);
        otp2.setStatus(false);
        otp_Service.update(otp2.getId(), otp2, cud_by);
    }

    private void sendSMS(@NonNull String mobileNumber, @NonNull String content, String cudBy) {
        if (enableSms) {
            smsTraceHelper.runWithTrace(List.of(mobileNumber),
                    content,
                    SmsTemplate.OTP,
                    cudBy,
                    () -> smsService.sendSMS(List.of(mobileNumber), content, SmsTemplate.OTP)
            );
        }
    }

    @NotNull
    private static HttpEntity<MultiValueMap<String, String>> getMultiValueMapHttpEntity(@NotNull String mobileNumber, @NotNull String content, HttpHeaders headers) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("sender_id", "STDZ");
        formData.add("message", "197532");
        formData.add("template_id", "1207175648734415953");
        formData.add("entity_id", "1201175208011209565");
        formData.add("route", "dlt");
        formData.add("numbers", mobileNumber);
        formData.add("variables_values", content);

        return new HttpEntity<>(formData, headers);
    }
}
