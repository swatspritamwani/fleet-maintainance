package com.fleet.maintenance.infrastructure.record;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureRecordTest {

    private static final long SAMPLE_TTL = 9_999_999L;

    @Test
    void maintenanceRequestRecordRoundTrip() {
        MaintenanceRequestRecord rec = new MaintenanceRequestRecord();
        rec.setPk("REQ#abc");
        rec.setSk("REQ#abc");
        rec.setStatus("CREATED");
        rec.setCreatedAt("2026-01-01T00:00:00Z");
        rec.setRequestId("abc");
        rec.setVehicleId("VH-001");
        rec.setDescription("desc");
        rec.setPriority("HIGH");
        rec.setAssignedProviderId("prov-1");
        rec.setCreatedBy("coord-1");
        rec.setUpdatedAt("2026-01-02T00:00:00Z");

        assertThat(rec.getPk()).isEqualTo("REQ#abc");
        assertThat(rec.getSk()).isEqualTo("REQ#abc");
        assertThat(rec.getStatus()).isEqualTo("CREATED");
        assertThat(rec.getCreatedAt()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(rec.getRequestId()).isEqualTo("abc");
        assertThat(rec.getVehicleId()).isEqualTo("VH-001");
        assertThat(rec.getDescription()).isEqualTo("desc");
        assertThat(rec.getPriority()).isEqualTo("HIGH");
        assertThat(rec.getAssignedProviderId()).isEqualTo("prov-1");
        assertThat(rec.getCreatedBy()).isEqualTo("coord-1");
        assertThat(rec.getUpdatedAt()).isEqualTo("2026-01-02T00:00:00Z");
    }

    @Test
    void inspectionReportRecordRoundTrip() {
        InspectionReportRecord rec = new InspectionReportRecord();
        rec.setPk("REQ#x");
        rec.setSk("INSP#y");
        rec.setReportId("y");
        rec.setRequestId("x");
        rec.setFindings("Oil low");
        rec.setEstimatedCostAmount("150.00");
        rec.setEstimatedCostCurrency("USD");
        rec.setEstimatedDurationDays(2);
        rec.setAttachments(List.of("http://s3/file.jpg"));
        rec.setSubmittedAt("2026-01-01T00:00:00Z");
        rec.setSubmittedBy("prov-1");

        assertThat(rec.getPk()).isEqualTo("REQ#x");
        assertThat(rec.getSk()).isEqualTo("INSP#y");
        assertThat(rec.getReportId()).isEqualTo("y");
        assertThat(rec.getRequestId()).isEqualTo("x");
        assertThat(rec.getFindings()).isEqualTo("Oil low");
        assertThat(rec.getEstimatedCostAmount()).isEqualTo("150.00");
        assertThat(rec.getEstimatedCostCurrency()).isEqualTo("USD");
        assertThat(rec.getEstimatedDurationDays()).isEqualTo(2);
        assertThat(rec.getAttachments()).containsExactly("http://s3/file.jpg");
        assertThat(rec.getSubmittedAt()).isEqualTo("2026-01-01T00:00:00Z");
        assertThat(rec.getSubmittedBy()).isEqualTo("prov-1");
    }

    @Test
    void decisionRecordRoundTrip() {
        DecisionRecord rec = new DecisionRecord();
        rec.setPk("REQ#x");
        rec.setSk("DEC#d");
        rec.setDecisionId("d");
        rec.setRequestId("x");
        rec.setOutcome("APPROVED");
        rec.setRemarks(null);
        rec.setDecidedBy("coord-1");
        rec.setDecidedAt("2026-01-01T00:00:00Z");

        assertThat(rec.getPk()).isEqualTo("REQ#x");
        assertThat(rec.getSk()).isEqualTo("DEC#d");
        assertThat(rec.getDecisionId()).isEqualTo("d");
        assertThat(rec.getOutcome()).isEqualTo("APPROVED");
        assertThat(rec.getRemarks()).isNull();
        assertThat(rec.getDecidedBy()).isEqualTo("coord-1");
        assertThat(rec.getDecidedAt()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void outboxEventRecordRoundTrip() {
        OutboxEventRecord rec = new OutboxEventRecord();
        rec.setPk("OUTBOX#e");
        rec.setSk("OUTBOX#e");
        rec.setStatus("PENDING");
        rec.setCreatedAt("2026-01-01T00:00:00Z");
        rec.setEventId("e");
        rec.setEventType("maintenance.request.created");
        rec.setKafkaTopic("maintenance.request.created");
        rec.setMessageKey("req-1");
        rec.setPayload("{\"eventId\":\"e\"}");
        rec.setPublishedAt(null);
        rec.setRetryCount(0);
        rec.setTtl(SAMPLE_TTL);

        assertThat(rec.getPk()).isEqualTo("OUTBOX#e");
        assertThat(rec.getStatus()).isEqualTo("PENDING");
        assertThat(rec.getEventType()).isEqualTo("maintenance.request.created");
        assertThat(rec.getKafkaTopic()).isEqualTo("maintenance.request.created");
        assertThat(rec.getMessageKey()).isEqualTo("req-1");
        assertThat(rec.getPayload()).contains("eventId");
        assertThat(rec.getPublishedAt()).isNull();
        assertThat(rec.getRetryCount()).isEqualTo(0);
        assertThat(rec.getTtl()).isEqualTo(SAMPLE_TTL);
    }

    @Test
    void serviceProviderRecordRoundTrip() {
        ServiceProviderRecord rec = new ServiceProviderRecord();
        rec.setPk("SP#p");
        rec.setSk("SP#p");
        rec.setProviderId("p");
        rec.setName("AutoFix");
        rec.setContactEmail("fix@example.com");
        rec.setPhone("555-0100");
        rec.setActive(true);

        assertThat(rec.getPk()).isEqualTo("SP#p");
        assertThat(rec.getSk()).isEqualTo("SP#p");
        assertThat(rec.getProviderId()).isEqualTo("p");
        assertThat(rec.getName()).isEqualTo("AutoFix");
        assertThat(rec.getContactEmail()).isEqualTo("fix@example.com");
        assertThat(rec.getPhone()).isEqualTo("555-0100");
        assertThat(rec.getActive()).isTrue();
    }
}
