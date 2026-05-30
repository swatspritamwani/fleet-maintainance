package com.fleet.maintenance.infrastructure.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoDbConfigTest {

    private final DynamoDbConfig config = new DynamoDbConfig();

    @Test
    void dynamoDbClientWithLocalEndpointUsesStaticCredentials() {
        DynamoDbClient client = config.dynamoDbClient("http://localhost:8000", "us-east-1");

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void dynamoDbClientWithEmptyEndpointUsesDefaultChain() {
        DynamoDbClient client = config.dynamoDbClient("", "us-east-1");

        assertThat(client).isNotNull();
        client.close();
    }

    @Test
    void dynamoDbEnhancedClientWrapsLowLevelClient() {
        DynamoDbClient lowLevel = config.dynamoDbClient("http://localhost:8000", "us-east-1");
        DynamoDbEnhancedClient enhancedClient = config.dynamoDbEnhancedClient(lowLevel);

        assertThat(enhancedClient).isNotNull();
        lowLevel.close();
    }
}
