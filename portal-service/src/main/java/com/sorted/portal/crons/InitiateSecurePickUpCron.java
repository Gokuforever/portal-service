package com.sorted.portal.crons;

import com.sorted.portal.service.secure.SecureReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//@Component
@Slf4j
@RequiredArgsConstructor
public class InitiateSecurePickUpCron {

    private final SecureReturnService secureReturnService;

    @Scheduled(fixedRate = 60000)
    public void initiateSecureReturns() {
        log.info("Initiating secure returns for orders.");
        secureReturnService.initiateSecurePickUp();
    }
}
