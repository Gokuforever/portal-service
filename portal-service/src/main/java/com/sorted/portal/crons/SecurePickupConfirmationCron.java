package com.sorted.portal.crons;

import com.sorted.portal.service.secure.SecureReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron job for secure pickup confirmation workflow:
 * 1. Sends confirmation emails to users with pickups scheduled for the next day
 * 2. Marks unconfirmed returns as PICKUP_NOT_CONFIRMED on the day of pickup
 */
//@Component
@Slf4j
@RequiredArgsConstructor
public class SecurePickupConfirmationCron {

    private final SecureReturnService secureReturnService;

    /**
     * Runs daily at 10:00 AM to send confirmation emails for next-day pickups
     */
//    @Scheduled(cron = "0 0 10 * * ?")
    @Scheduled(fixedRate = 60000)
    public void sendPickupConfirmationEmails() {
        log.info("Starting secure pickup confirmation email cron job");
        try {
            secureReturnService.sendPickupConfirmationEmails();
        } catch (Exception e) {
            log.error("Error in secure pickup confirmation email cron job", e);
        }
        log.info("Completed secure pickup confirmation email cron job");
    }

    /**
     * Runs daily at 6:00 AM to mark unconfirmed returns as PICKUP_NOT_CONFIRMED
     * This runs before the pickup initiation cron to ensure unconfirmed pickups are not processed
     */
    @Scheduled(fixedRate = 60000)
    public void markUnconfirmedReturns() {
        log.info("Starting mark unconfirmed returns cron job");
        try {
            secureReturnService.markUnconfirmedReturns();
        } catch (Exception e) {
            log.error("Error in mark unconfirmed returns cron job", e);
        }
        log.info("Completed mark unconfirmed returns cron job");
    }
}
