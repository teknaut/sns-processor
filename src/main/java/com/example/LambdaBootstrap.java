package com.example;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.example.handler.S3SnsEventHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Minimal custom Lambda runtime for GraalVM native-image.
 *
 * Replaces the aws-lambda-java-runtime-interface-client, which uses heavy
 * internal reflection that is incompatible with native-image. This runtime
 * polls the Lambda Runtime API directly, deserializes the SNSEvent, and
 * dispatches to the handler — no reflection involved.
 *
 * Runtime API docs:
 * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html
 */
public class LambdaBootstrap {

    private static final String BASE_URL =
        "http://" + System.getenv("AWS_LAMBDA_RUNTIME_API") + "/2018-06-01/runtime";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .addModule(new JodaModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .build();

    public static void main(String[] args) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        S3SnsEventHandler handler = new S3SnsEventHandler();

        while (true) {
            // Block until the next invocation is available
            HttpResponse<String> invocation = http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/invocation/next")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            String requestId = invocation.headers()
                .firstValue("Lambda-Runtime-Aws-Request-Id")
                .orElseThrow(() -> new IllegalStateException("Missing Lambda-Runtime-Aws-Request-Id header"));

            try {
                SNSEvent event = MAPPER.readValue(invocation.body(), SNSEvent.class);
                handler.handleRequest(event, new CustomContext(requestId));
                reportSuccess(http, requestId);
            } catch (Exception e) {
                reportError(http, requestId, e);
            }
        }
    }

    private static void reportSuccess(HttpClient http, String requestId) throws Exception {
        http.send(
            HttpRequest.newBuilder(URI.create(BASE_URL + "/invocation/" + requestId + "/response"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("null"))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        );
    }

    private static void reportError(HttpClient http, String requestId, Exception e) throws Exception {
        // Use plain string formatting here — never rely on Jackson in the error path
        e.printStackTrace(System.err);
        String errorType    = e.getClass().getName();
        String errorMessage = (e.getMessage() != null ? e.getMessage() : "Unknown error")
            .replace("\\", "\\\\").replace("\"", "\\\"");
        String payload = String.format(
            "{\"errorType\":\"%s\",\"errorMessage\":\"%s\"}", errorType, errorMessage
        );
        http.send(
            HttpRequest.newBuilder(URI.create(BASE_URL + "/invocation/" + requestId + "/error"))
                .header("Content-Type", "application/json")
                .header("Lambda-Runtime-Function-Error-Type", "RuntimeException")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(),
            HttpResponse.BodyHandlers.discarding()
        );
    }
}
