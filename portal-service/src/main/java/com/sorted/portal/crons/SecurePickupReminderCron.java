package com.sorted.portal.crons;

import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.*;
import com.sorted.common.enums.MailTemplate;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.enums.PurchaseType;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.MailBuilder;
import com.sorted.common.notifications.EmailSenderImpl;
import com.sorted.common.utils.InternalMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cron job to send reminder emails to customers about secure pickup eligibility.
 * 
 * SecuRe is a feature that allows customers to return products marked as secure
 * within 180 days of purchase. This cron job identifies delivered orders with
 * secure items and sends reminder emails to customers who haven't initiated a return yet.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurePickupReminderCron {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSenderImpl;
    private final InternalMailService internalMailService;

    /**
     * Sends reminder emails to customers about scheduled secure pickup.
     * Runs daily at 10 AM to remind customers about their secure return pickups scheduled for tomorrow.
     * Filters orders based on secured_date and secured_time_slot.
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void sendSecurePickupReminders() {
        log.info("Starting secure pickup reminder cron job");

        try {
            // Get tomorrow's date for the reminder
            java.time.LocalDate tomorrowDate = java.time.LocalDate.now().plusDays(1);
            
            // Find all orders with secure return scheduled for tomorrow
            SEFilter orderFilter = new SEFilter(SEFilterType.AND);
            orderFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            orderFilter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.SECURE_RETURN_SCHEDULED.getId()));
            orderFilter.addClause(WhereClause.eq(Order_Details.Fields.secured_date, tomorrowDate));
            orderFilter.addClause(WhereClause.notEq(Order_Details.Fields.secured_time_slot, null));

            List<Order_Details> scheduledOrders = orderDetailsService.repoFind(orderFilter);

            if (CollectionUtils.isEmpty(scheduledOrders)) {
                log.info("No secure return pickups scheduled for tomorrow ({})", tomorrowDate);
                return;
            }

            log.info("Found {} secure return pickups scheduled for tomorrow ({})", scheduledOrders.size(), tomorrowDate);

            // Get all order IDs
            List<String> orderIds = scheduledOrders.stream()
                    .map(Order_Details::getId)
                    .toList();

            // Fetch all order items for these orders (only SECURE purchase type)
            SEFilter itemFilter = new SEFilter(SEFilterType.AND);
            itemFilter.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
            itemFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            itemFilter.addClause(WhereClause.eq(Order_Item.Fields.status_id, OrderStatus.DELIVERED.getId()));
            itemFilter.addClause(WhereClause.eq(Order_Item.Fields.type, PurchaseType.SECURE));

            List<Order_Item> orderItems = orderItemService.repoFind(itemFilter);

            if (CollectionUtils.isEmpty(orderItems)) {
                log.info("No secure order items found for scheduled pickups");
                return;
            }

            log.info("Found {} secure order items", orderItems.size());

            // Group order items by order ID (all items are already filtered by PurchaseType.SECURE)
            Map<String, List<Order_Item>> orderItemsMap = orderItems.stream()
                    .collect(Collectors.groupingBy(Order_Item::getOrder_id));



            // Filter orders that have secure items
            List<Order_Details> ordersWithSecureItems = scheduledOrders.stream()
                    .filter(order -> orderItemsMap.containsKey(order.getId()))
                    .toList();

            if (CollectionUtils.isEmpty(ordersWithSecureItems)) {
                log.info("No orders with secure items to send reminders");
                return;
            }

            log.info("Found {} orders with secure items to send reminders", ordersWithSecureItems.size());

            // Get unique user IDs
            List<String> userIds = ordersWithSecureItems.stream()
                    .map(Order_Details::getUser_id)
                    .distinct()
                    .toList();

            // Fetch user details
            SEFilter userFilter = new SEFilter(SEFilterType.AND);
            userFilter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
            userFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<Users> users = usersService.repoFind(userFilter);
            Map<String, Users> userMap = users.stream()
                    .collect(Collectors.toMap(Users::getId, user -> user));

            // Send reminder emails to customers
            int emailsSent = 0;
            for (Order_Details order : ordersWithSecureItems) {
                Users user = userMap.get(order.getUser_id());
                if (user == null || !StringUtils.hasText(user.getEmail_id())) {
                    log.warn("User not found or email missing for order: {}", order.getId());
                    continue;
                }

                try {
                    // Get secure items for this order
                    List<Order_Item> secureItems = orderItemsMap.get(order.getId());
                    int secureItemCount = secureItems != null ? secureItems.size() : 0;

                    // Get scheduled pickup details
                    java.time.LocalDate pickupDate = order.getSecured_date();
                    String timeSlot = order.getSecured_time_slot() != null ? order.getSecured_time_slot().getDisplayName() : "Not specified";

                    // Build email content with template variables
                    String customerName = user.getFirst_name();
                    String orderCode = order.getCode();
                    String emailContent = buildSecureReminderEmailContent(customerName, orderCode, secureItemCount, pickupDate, timeSlot);

                    // Send email
                    MailBuilder mailBuilder = new MailBuilder();
                    mailBuilder.setTo(user.getEmail_id());
                    mailBuilder.setContent(emailContent); // Template variables: name|orderCode|itemCount|date|timeSlot
                    mailBuilder.setTemplate(MailTemplate.SECURE_PICKUP_REMINDER);

                    emailSenderImpl.sendEmailHtmlTemplate(mailBuilder);
                    emailsSent++;

                    log.info("Sent secure pickup reminder to user: {} for order: {}", user.getEmail_id(), order.getId());
                } catch (Exception e) {
                    log.error("Failed to send reminder email for order: {}, Error: {}", order.getId(), e.getMessage(), e);
                }
            }

            log.info("Secure pickup reminder cron job completed. Emails sent: {}", emailsSent);
        } catch (Exception e) {
            log.error("Error in secure pickup reminder cron job: {}", e.getMessage(), e);
            internalMailService.sendMailOnError("Secure Pickup Reminder Cron Failed", 
                    "Error in secure pickup reminder cron: " + e.getMessage(), e);
        }
    }

    /**
     * Builds email content for secure pickup reminder
     * Template variables: customerName|orderCode|itemCount|pickupDate|timeSlot
     * 
     * @param customerName Name of the customer
     * @param orderCode Order code
     * @param secureItemCount Number of secure items in the order
     * @param pickupDate Scheduled pickup date
     * @param timeSlot Scheduled time slot
     * @return Pipe-separated template variables
     */
    private String buildSecureReminderEmailContent(String customerName, String orderCode, int secureItemCount, java.time.LocalDate pickupDate, String timeSlot) {
        // Template variables separated by pipe (|)
        // The actual HTML template should be defined in secure_pickup_reminder.html
        return customerName + "|" + 
               orderCode + "|" + 
               secureItemCount + "|" + 
               pickupDate + "|" + 
               timeSlot;
    }
}
