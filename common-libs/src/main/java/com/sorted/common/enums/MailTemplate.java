package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MailTemplate {
	SIGN_UP_COMPLETED("sign_up_completed.html", "Welcome to the world of Studeaze!"),
	DIRECT_ORDER_CONFIRMATION("direct_order_confirmation.html", "Your Order Confirmation - Let’s Hit the Exams!"),
	SECURED_ORDER_CONFIRMATION("secure_order_confirmation.html", "Your Order is SecuRed – Time to Save Big and Smash Those Exams!"),
	ORDER_DISPATCHED("order_dispatched.html", "Your Order’s on The Way!"),
	ORDER_ARRIVED("order_arrived.html", "Your Studeaze Order has Arrived – Let the Learning Begin!"),
	ORDER_REJECTED("order_rejected.html", "Update on Your Recent Order – Refund Initiated"),
	DELIVERY_FAILED("delivery_failed.html", "Delivery was Unsuccessful!"),
	NEW_ORDER_ARRIVED("new_order_arrived.html", "You’ve Got a New Order from Studeaze!"),
	SELLER_WELCOME_MAIL("welcome_mail.html", "Welcome Aboard! Let’s Grow Together with Studeaze!"),
	LAUNCHING_MAIL("launch.html", "Your Student Life, Officially Simplified. Studeaze is Here"),
	AMBASSADOR_WELCOME_MAIL("ambassador_welcome_mail.html", "Welcome to the Studeaze Campus Ambassador Family!"),
	SECURE_PICKUP_REMINDER("secure_pickup_reminder.html", "Reminder: Your SecuRe Pickup is Scheduled for Tomorrow!"),
	ERROR("error.html", "Error occurred on website.");

	private final String file_name;
	private final String subject;
}