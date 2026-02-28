package com.example.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

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
            S3EventNotification s3Notification = S3EventNotification.parseJson(message);

            for (S3EventNotificationRecord s3Record : s3Notification.getRecords()) {
                String eventName = s3Record.getEventName();
                String bucket   = s3Record.getS3().getBucket().getName();
                // S3 URL-encodes object keys in event notifications; decode as needed
                String key      = s3Record.getS3().getObject().getKey();
                Long   size     = s3Record.getS3().getObject().getSize();

                logger.log(String.format(
                    "S3 Event | type=%s | bucket=%s | key=%s | size=%d bytes%n",
                    eventName, bucket, key, size
                ));

                // TODO: add your business logic here (e.g. trigger processing pipeline,
                //       update a database, publish a downstream event, etc.)
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
