package soa.enrollmentservice.dto;

import java.math.BigDecimal;

public class PaymentClientRequest {

    private Long enrollmentId;
    private BigDecimal amount;
    private String paymentStatus;
    private String idempotencyKey;
    private String paymentMethod;
    private String testScenario;

    public PaymentClientRequest() {
    }

    public PaymentClientRequest(Long enrollmentId, BigDecimal amount, String paymentStatus) {
        this.enrollmentId = enrollmentId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
    }

    public PaymentClientRequest(Long enrollmentId, BigDecimal amount, String paymentStatus, String idempotencyKey, String testScenario) {
        this.enrollmentId = enrollmentId;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.idempotencyKey = idempotencyKey;
        this.testScenario = testScenario;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTestScenario() {
        return testScenario;
    }

    public void setTestScenario(String testScenario) {
        this.testScenario = testScenario;
    }
}
