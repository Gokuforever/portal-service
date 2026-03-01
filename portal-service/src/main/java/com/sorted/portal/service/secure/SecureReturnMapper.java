package com.sorted.portal.service.secure;

import com.sorted.common.beans.Secure_Return_Item;
import com.sorted.common.entity.mongo.Secure_Return;
import com.sorted.portal.response.beans.SecureReturnDTO;
import com.sorted.portal.response.beans.SecureReturnItemDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper to convert Secure_Return entity to DTOs.
 */
@Component
public class SecureReturnMapper {

    /**
     * Convert Secure_Return entity to DTO
     */
    public SecureReturnDTO toDTO(Secure_Return secureReturn) {
        if (secureReturn == null) {
            return null;
        }

        return SecureReturnDTO.builder()
                .id(secureReturn.getId())
                .order_id(secureReturn.getOrder_id())
                .order_code(secureReturn.getOrder_code())
                .secure_order_code(secureReturn.getSecure_order_code())
                .user_id(secureReturn.getUser_id())
                .seller_id(secureReturn.getSeller_id())
                .status(secureReturn.getStatus())
                .status_description(secureReturn.getStatus() != null ? secureReturn.getStatus().getDescription() : null)
                .scheduled_pickup_date(secureReturn.getScheduled_pickup_date())
                .scheduled_time_slot(secureReturn.getScheduled_time_slot())
                .actual_pickup_time(secureReturn.getActual_pickup_time())
                .reschedule_count(secureReturn.getReschedule_count())
                .max_reschedule_allowed(secureReturn.getMax_reschedule_allowed())
                .can_reschedule(secureReturn.canReschedule())
                .pickup_address(secureReturn.getPickup_address())
                .delivery_address(secureReturn.getDelivery_address())
                .dp_order_id(secureReturn.getDp_order_id())
                .dp_tracking_url(secureReturn.getDp_tracking_url())
                .estimated_delivery_charges(secureReturn.getEstimated_delivery_charges())
                .actual_delivery_charges(secureReturn.getActual_delivery_charges())
                .items(toItemDTOList(secureReturn.getItems()))
                .total_items_count(CollectionUtils.isEmpty(secureReturn.getItems()) ? 0 : secureReturn.getItems().size())
                .total_estimated_refund(secureReturn.getTotal_estimated_refund())
                .total_actual_refund(secureReturn.getTotal_actual_refund())
                .refund_transaction_id(secureReturn.getRefund_transaction_id())
                .refund_status(secureReturn.getRefund_status())
                .refund_status_description(secureReturn.getRefund_status() != null ? secureReturn.getRefund_status().getDescription() : null)
                .refund_initiated_at(secureReturn.getRefund_initiated_at())
                .refund_completed_at(secureReturn.getRefund_completed_at())
                .failure_reason(secureReturn.getFailure_reason())
                .rejection_remarks(secureReturn.getRejection_remarks())
                .created_at(secureReturn.getCreation_date())
                .updated_at(secureReturn.getModification_date())
                .build();
    }

    /**
     * Convert list of Secure_Return entities to DTOs
     */
    public List<SecureReturnDTO> toDTOList(List<Secure_Return> secureReturns) {
        if (CollectionUtils.isEmpty(secureReturns)) {
            return Collections.emptyList();
        }
        return secureReturns.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert Secure_Return_Item to DTO
     */
    public SecureReturnItemDTO toItemDTO(Secure_Return_Item item) {
        if (item == null) {
            return null;
        }

        return SecureReturnItemDTO.builder()
                .order_item_id(item.getOrder_item_id())
                .product_id(item.getProduct_id())
                .product_code(item.getProduct_code())
                .product_name(item.getProduct_name())
                .product_image_url(item.getProduct_image_url())
                .quantity(item.getQuantity())
                .selling_price_after_discount(item.getSelling_price_after_discount())
                .total_item_cost(item.getTotal_item_cost())
                .appraisal_grade(item.getAppraisal_grade())
                .appraisal_grade_description(item.getAppraisal_grade() != null ? item.getAppraisal_grade().getDescription() : null)
                .appraisal_remarks(item.getAppraisal_remarks())
                .returned_item_image_urls(item.getReturned_item_image_urls())
                .appraised_at(item.getAppraised_at())
                .appraised_by(item.getAppraised_by())
                .estimated_refund_amount(item.getEstimated_refund_amount())
                .actual_refund_amount(item.getActual_refund_amount())
                .refund_percentage(item.getRefund_percentage())
                .item_status(item.getItem_status())
                .item_status_description(item.getItem_status() != null ? item.getItem_status().getDescription() : null)
                .build();
    }

    /**
     * Convert list of items to DTOs
     */
    public List<SecureReturnItemDTO> toItemDTOList(List<Secure_Return_Item> items) {
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());
    }
}
