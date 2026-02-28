package com.example.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * Handles S3 event notifications delivered via SNS.
 *
 * Flow: S3 → SNS Topic → this Lambda
 *
 * The SNS message body contains a JSON-encoded S3EventNotification.
 * Each SNS record can contain multiple S3 event records.
 */
public class S3SnsEventHandler implements RequestHandler<SNSEvent, Void> {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // SNS/S3 event JSON uses PascalCase keys (e.g. "Records", "Sns")
            // while the Java fields use camelCase — enable case-insensitive matching
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .build();

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        LambdaLogger logger = context.getLogger();
        List<SNSEvent.SNSRecord> snsRecords = event.getRecords();

        logger.log(String.format("Received %d SNS record(s)%n", snsRecords.size()));

        for (SNSEvent.SNSRecord snsRecord : snsRecords) {
            processRecord(snsRecord, logger);
        }

        return null;
    }

    private void processRecord(SNSEvent.SNSRecord snsRecord, LambdaLogger logger) {
        String messageId = snsRecord.getSNS().getMessageId();
        String message = snsRecord.getSNS().getMessage();

        logger.log(String.format("Processing SNS message: %s%n", messageId));

        try {
            // Parse the inner S3 notification using the tree model to avoid
            // S3EventNotification's @JsonCreator reflection issues in native-image
            JsonNode s3Notification = OBJECT_MAPPER.readTree(message);

            for (JsonNode s3Record : s3Notification.get("Records")) {
                String eventName = s3Record.get("eventName").asText();
                String bucket    = s3Record.get("s3").get("bucket").get("name").asText();
                // S3 URL-encodes object keys in event notifications; decode as needed
                String key       = s3Record.get("s3").get("object").get("key").asText();
                long   size      = s3Record.get("s3").get("object").get("size").asLong();

                logger.log(String.format(
                    "S3 Event | type=%s | bucket=%s | key=%s | size=%d bytes%n",
                    eventName, bucket, key, size
                ));

                // TODO: add your business logic here
            }

        } catch (Exception e) {
            logger.log(String.format(
                "ERROR processing SNS message %s: %s%n", messageId, e.getMessage()
            ));
            // Re-throw so Lambda marks the invocation as failed and SNS can retry
            throw new RuntimeException("Failed to process S3 event from SNS message: " + messageId, e);
        }
    }
}
