package soa.enrollmentservice.dto;

import java.math.BigDecimal;

public class PaymentClientRequest {

    private Long enrollmentId;
    private BigDecimal amount;
    private String paymentStatus;

    public PaymentClientRequest() {
    }

    public PaymentClientRequest(Long enrollmentId, BigDecimal amount, String paymentStatus) {
        this.enrollmentId = enrollmentId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
    }

    public Long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
