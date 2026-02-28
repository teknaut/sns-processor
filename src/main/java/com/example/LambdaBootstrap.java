package com.example;

import com.example.handler.S3EventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Minimal custom Lambda runtime for GraalVM native-image.
 *
 * Polls the Lambda Runtime API, deserializes the S3 event payload as a
 * JsonNode, and dispatches to S3EventHandler. Using JsonNode avoids all
 * class-binding reflection issues with native-image.
 *
 * Runtime API docs:
 * https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html
 */
public class LambdaBootstrap {

    private static final String BASE_URL =
        "http://" + System.getenv("AWS_LAMBDA_RUNTIME_API") + "/2018-06-01/runtime";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        S3EventHandler handler = new S3EventHandler();

        while (true) {
            HttpResponse<String> invocation = http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + "/invocation/next")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            );

            String requestId = invocation.headers()
                .firstValue("Lambda-Runtime-Aws-Request-Id")
                .orElseThrow(() -> new IllegalStateException("Missing Lambda-Runtime-Aws-Request-Id header"));

            try {
                JsonNode event = MAPPER.readTree(invocation.body());
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
