package com.example;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Minimal Lambda Context for the custom native-image runtime.
 * Reads standard Lambda environment variables where applicable.
 */
public class CustomContext implements Context {

    private final String requestId;
    private final LambdaLogger logger = new LambdaLogger() {
        @Override public void log(String message) { System.out.print(message); }
        @Override public void log(byte[] message) { System.out.write(message, 0, message.length); }
    };

    public CustomContext(String requestId) {
        this.requestId = requestId;
    }

    @Override public String getAwsRequestId()       { return requestId; }
    @Override public LambdaLogger getLogger()        { return logger; }
    @Override public String getFunctionName()        { return System.getenv("AWS_LAMBDA_FUNCTION_NAME"); }
    @Override public String getFunctionVersion()     { return System.getenv("AWS_LAMBDA_FUNCTION_VERSION"); }
    @Override public String getLogGroupName()        { return System.getenv("AWS_LAMBDA_LOG_GROUP_NAME"); }
    @Override public String getLogStreamName()       { return System.getenv("AWS_LAMBDA_LOG_STREAM_NAME"); }
    @Override public int getMemoryLimitInMB()        { return Integer.parseInt(System.getenv().getOrDefault("AWS_LAMBDA_FUNCTION_MEMORY_SIZE", "256")); }
    @Override public String getInvokedFunctionArn()  { return ""; }
    @Override public CognitoIdentity getIdentity()   { return null; }
    @Override public ClientContext getClientContext() { return null; }
    @Override public int getRemainingTimeInMillis()  { return 0; }
}
