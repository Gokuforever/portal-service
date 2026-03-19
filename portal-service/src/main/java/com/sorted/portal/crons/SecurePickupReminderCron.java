package com.sorted.portal.crons;

import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.notifications.EmailSenderImpl;
import com.sorted.common.utils.InternalMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Cron job to send reminder emails to customers about secure pickup eligibility.
 * <p>
 * SecuRe is a feature that allows customers to return products marked as secure
 * within 180 days of purchase. This cron job identifies delivered orders with
 * secure items and sends reminder emails to customers who haven't initiated a return yet.
 */
//@Component
@Slf4j
@RequiredArgsConstructor
public class SecurePickupReminderCron {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSenderImpl;
    private final InternalMailService internalMailService;



}
