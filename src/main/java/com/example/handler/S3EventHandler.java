package com.example.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handles S3 event notifications delivered directly from S3.
 *
 * Flow: S3 → this Lambda
 *
 * The event payload is a standard S3 notification containing one or more
 * S3EventRecord objects, parsed here using Jackson's tree model to avoid
 * reflection issues with native-image.
 */
public class S3EventHandler {

    public void handleRequest(JsonNode event, Context context) {
        LambdaLogger logger = context.getLogger();
        JsonNode records = event.get("Records");

        if (records == null || !records.isArray() || records.isEmpty()) {
            logger.log("No Records found in S3 event\n");
            return;
        }

        logger.log(String.format("Received %d S3 record(s)%n", records.size()));

        for (JsonNode record : records) {
            String eventName = record.get("eventName").asText();
            String bucket    = record.get("s3").get("bucket").get("name").asText();
            // S3 URL-encodes object keys in event notifications; decode as needed
            String key       = record.get("s3").get("object").get("key").asText();
            long   size      = record.get("s3").get("object").get("size").asLong();

            logger.log(String.format(
                "S3 Event | type=%s | bucket=%s | key=%s | size=%d bytes%n",
                eventName, bucket, key, size
            ));

            // TODO: add your business logic here
        }
    }
}
