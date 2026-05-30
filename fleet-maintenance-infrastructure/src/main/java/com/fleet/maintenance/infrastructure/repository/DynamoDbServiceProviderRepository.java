package com.fleet.maintenance.infrastructure.repository;

import com.fleet.maintenance.domain.model.ServiceProvider;
import com.fleet.maintenance.domain.port.ServiceProviderRepository;
import com.fleet.maintenance.infrastructure.record.ServiceProviderRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DynamoDbServiceProviderRepository implements ServiceProviderRepository {

    private static final String SP_PREFIX = "SP#";

    private final DynamoDbTable<ServiceProviderRecord> providerTable;

    public DynamoDbServiceProviderRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${fleet.dynamodb.table-name:fleet-maintenance}") String tableName) {
        this.providerTable = enhancedClient.table(tableName, TableSchema.fromBean(ServiceProviderRecord.class));
    }

    @Override
    public Optional<ServiceProvider> findById(UUID providerId) {
        String keyValue = SP_PREFIX + providerId;
        Key key = Key.builder().partitionValue(keyValue).sortValue(keyValue).build();
        ServiceProviderRecord record = providerTable.getItem(key);
        return Optional.ofNullable(record).map(this::fromRecord);
    }

    @Override
    public List<ServiceProvider> findAllActive() {
        return providerTable.scan(ScanEnhancedRequest.builder().build())
            .stream()
            .flatMap(page -> page.items().stream())
            .map(this::fromRecord)
            .filter(ServiceProvider::active)
            .toList();
    }

    private ServiceProvider fromRecord(ServiceProviderRecord rec) {
        return new ServiceProvider(
            UUID.fromString(rec.getProviderId()),
            rec.getName(),
            rec.getContactEmail(),
            rec.getPhone(),
            Boolean.TRUE.equals(rec.getActive())
        );
    }
}
