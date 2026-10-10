
package com.rewin.itsm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

@Service
public class EventBridgePublisher {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String region;
    private final String eventBusName;

    
    public EventBridgePublisher(
            ObjectMapper objectMapper,
            @Value("${aws.eventbridge.enabled:false}") boolean enabled,
            @Value("${aws.eventbridge.region:ap-south-1}") String region,
            @Value("${aws.eventbridge.event-bus-name:default}") String eventBusName) {

        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.region = region;
        this.eventBusName = eventBusName;
    }

    public void publishSlaBreach(
            String incidentId,
            String stage,
            String recipientEmail) {

        if (!enabled || recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("incidentId", incidentId);
        detail.put("stage", stage);
        detail.put("status", "BREACHED");
        detail.put("recipientEmail", recipientEmail);

        try {
            String detailJson = objectMapper.writeValueAsString(detail);

            try (ProfileCredentialsProvider credentialsProvider =
                    ProfileCredentialsProvider.builder()
                        .profileName("java-process")
                        .build();
                EventBridgeClient client = EventBridgeClient.builder()
                    .region(software.amazon.awssdk.regions.Region.of(region))
                    .credentialsProvider(credentialsProvider)
                    .build()) {
                PutEventsResponse response = client.putEvents(
                    PutEventsRequest.builder()
                        .entries(PutEventsRequestEntry.builder()
                            .eventBusName(eventBusName)
                            .source("resolvex.itsm")
                            .detailType("SLA Breach")
                            .detail(detailJson)
                            .build())
                        .build());

                if (response.failedEntryCount() != null
                        && response.failedEntryCount() > 0) {
                    throw new IllegalStateException(
                        "EventBridge failed to accept the SLA-breach event");
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Could not serialize the SLA-breach event", e);
        }
    }
}
