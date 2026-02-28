package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class SettlementDetails {
    @JsonProperty("payment_mode")
    private PaymentMode paymentMode;
    @JsonProperty("beneficiary_name")
    private String beneficiaryName;
    private String vpa;
    @JsonProperty("account_number")
    private String accountNumber;
    @JsonProperty("ifsc_code")
    private String ifscCode;
    @JsonProperty("txn_id")
    private String txnId;
    @JsonProperty("txn_date")
    private String txnDate;
    private String remarks;
    @JsonProperty("txn_ss_id")
    private String txnSsId;
    @JsonProperty("cheque_number")
    private String chequeNumber;
    private BigDecimal amount;

    public SettlementDetails(SettlementDetails settlementDetails) {
        this.paymentMode = settlementDetails.getPaymentMode();
        this.amount = settlementDetails.getAmount();
        this.remarks = settlementDetails.getRemarks();
        this.txnDate = settlementDetails.getTxnDate();
        this.beneficiaryName = settlementDetails.getBeneficiaryName();
        this.txnSsId = settlementDetails.getTxnSsId();

        switch (this.getPaymentMode()) {
            case UPI -> {
                this.vpa = settlementDetails.getVpa();
                this.txnId = settlementDetails.getTxnId();
            }
            case IMPS, NEFT, RTGS -> {
                this.accountNumber = settlementDetails.getAccountNumber();
                this.ifscCode = settlementDetails.getIfscCode();
                this.txnId = settlementDetails.getTxnId();
            }
            default -> {
                this.accountNumber = settlementDetails.getAccountNumber();
                this.ifscCode = settlementDetails.getIfscCode();
                this.chequeNumber = settlementDetails.getChequeNumber();
            }
        }
    }
}
