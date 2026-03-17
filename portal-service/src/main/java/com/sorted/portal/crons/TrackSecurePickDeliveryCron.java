package com.sorted.portal.crons;

import com.sorted.portal.service.secure.SecureReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class TrackSecurePickDeliveryCron {

    private final SecureReturnService secureReturnService;

    @Scheduled(fixedRate = 60000)
    public void trackDelivery() {
        log.info("trackDelivery started: {}", LocalDateTime.now());
        secureReturnService.trackDelivery();
    }
}
