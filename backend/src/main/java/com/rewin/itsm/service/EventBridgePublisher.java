
package com.rewin.itsm.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publishes SLA-breach events to EventBridge, where a Lambda turns them into
 * emails.
 *
 * <p>Credentials come from the default AWS provider chain: the EC2 instance role
 * when deployed, or {@code AWS_PROFILE} / environment variables locally. Nothing
 * is hardcoded.
 *
 * <p>A failed publish is logged, not thrown. This is called from inside the SLA
 * sweep's transaction, and an email that could not be sent must not roll back
 * the breach that was recorded - nor the sweep of every other incident.
 */
@Service
public class EventBridgePublisher {

    private static final Logger log = LoggerFactory.getLogger(EventBridgePublisher.class);

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

            try (EventBridgeClient client = EventBridgeClient.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.builder().build())
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
                    log.warn("EventBridge rejected the SLA-breach event for {} ({} stage)",
                        incidentId, stage);
                }
            }
        } catch (JacksonException | SdkException e) {
            log.warn("Could not publish the SLA-breach event for {} ({} stage)",
                incidentId, stage, e);
        }
    }
}
