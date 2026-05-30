package com.fleet.maintenance.infrastructure.record;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;

@DynamoDbBean
public class InspectionReportRecord {

    private String pk;
    private String sk;
    private String reportId;
    private String requestId;
    private String findings;
    private String estimatedCostAmount;
    private String estimatedCostCurrency;
    private Integer estimatedDurationDays;
    private List<String> attachments;
    private String submittedAt;
    private String submittedBy;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("PK")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("SK")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getFindings() {
        return findings;
    }

    public void setFindings(String findings) {
        this.findings = findings;
    }

    public String getEstimatedCostAmount() {
        return estimatedCostAmount;
    }

    public void setEstimatedCostAmount(String estimatedCostAmount) {
        this.estimatedCostAmount = estimatedCostAmount;
    }

    public String getEstimatedCostCurrency() {
        return estimatedCostCurrency;
    }

    public void setEstimatedCostCurrency(String estimatedCostCurrency) {
        this.estimatedCostCurrency = estimatedCostCurrency;
    }

    public Integer getEstimatedDurationDays() {
        return estimatedDurationDays;
    }

    public void setEstimatedDurationDays(Integer estimatedDurationDays) {
        this.estimatedDurationDays = estimatedDurationDays;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }
}
