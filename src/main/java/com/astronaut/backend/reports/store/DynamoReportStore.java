package com.astronaut.backend.reports.store;

import com.astronaut.backend.reports.config.ReportsProperties;
import com.astronaut.backend.reports.model.AstronautReading;
import com.astronaut.backend.reports.model.ReportComment;
import com.astronaut.backend.reports.model.ReportDocument;
import com.astronaut.backend.reports.model.ReportMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.astronaut.backend.reports.store.DynamoItemMapper.COMMENT_PREFIX;
import static com.astronaut.backend.reports.store.DynamoItemMapper.META_SK;
import static com.astronaut.backend.reports.store.DynamoItemMapper.PK;
import static com.astronaut.backend.reports.store.DynamoItemMapper.READING_PREFIX;
import static com.astronaut.backend.reports.store.DynamoItemMapper.SK;
import static com.astronaut.backend.reports.store.DynamoItemMapper.n;
import static com.astronaut.backend.reports.store.DynamoItemMapper.s;

/**
 * Informes en Amazon DynamoDB (tabla única, ver {@link DynamoItemMapper}).
 * Si la tabla no existe y reports.dynamodb.create-table=true, la crea (on-demand + TTL).
 */
@Component("dynamoReportStore")
public class DynamoReportStore implements ReportStore {

    private static final Logger log = LoggerFactory.getLogger(DynamoReportStore.class);
    private static final int BATCH_SIZE = 25; // límite de BatchWriteItem

    private final DynamoDbClient dynamo;
    private final String table;
    private final boolean createTable;
    private volatile boolean tableReady;

    public DynamoReportStore(DynamoDbClient dynamo, ReportsProperties properties) {
        this.dynamo = dynamo;
        this.table = properties.dynamodb().table();
        this.createTable = !Boolean.FALSE.equals(properties.dynamodb().createTable());
    }

    public String table() {
        return table;
    }

    @Override
    public boolean ping() {
        try {
            if (!tableReady) {
                ensureTable();
            }
            TableStatus status = dynamo.describeTable(r -> r.tableName(table)).table().tableStatus();
            tableReady = status == TableStatus.ACTIVE || status == TableStatus.UPDATING;
            return tableReady;
        } catch (RuntimeException e) {
            tableReady = false;
            log.debug("DynamoDB no disponible: {}", e.getMessage());
            return false;
        }
    }

    private void ensureTable() {
        try {
            dynamo.describeTable(r -> r.tableName(table));
        } catch (ResourceNotFoundException notFound) {
            if (!createTable) {
                throw notFound;
            }
            log.info("Creando tabla DynamoDB '{}'...", table);
            dynamo.createTable(CreateTableRequest.builder()
                    .tableName(table)
                    .keySchema(
                            KeySchemaElement.builder().attributeName(PK).keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName(SK).keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName(PK).attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName(SK).attributeType(ScalarAttributeType.S).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            dynamo.waiter().waitUntilTableExists(r -> r.tableName(table));
            try {
                dynamo.updateTimeToLive(r -> r.tableName(table).timeToLiveSpecification(
                        TimeToLiveSpecification.builder().attributeName(DynamoItemMapper.TTL).enabled(true).build()));
            } catch (RuntimeException e) {
                log.warn("No se pudo activar el TTL de la tabla: {}", e.getMessage());
            }
        }
    }

    @Override
    public void createIfAbsent(ReportMeta meta) {
        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(DynamoItemMapper.metaItem(meta))
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", PK))
                    .build());
        } catch (ConditionalCheckFailedException alreadyExists) {
            // ya existe: nada que hacer
        }
    }

    @Override
    public void addReadings(ReportMeta meta, List<AstronautReading> readings, Instant updatedAt) {
        if (readings.isEmpty()) {
            return;
        }
        batchPut(readings.stream().map(r -> DynamoItemMapper.readingItem(meta, r)).toList());
        List<String> ids = readings.stream()
                .map(r -> r.astronaut().id()).filter(java.util.Objects::nonNull)
                .distinct().map(String::valueOf).toList();

        Map<String, String> names = new HashMap<>(Map.of("#u", "updatedAt", "#rc", "readingCount", "#ttl", "ttl"));
        Map<String, AttributeValue> values = new HashMap<>(Map.of(
                ":u", s(updatedAt.toString()),
                ":n", n(readings.size()),
                ":ttl", n(meta.expiresAtEpoch())));
        String expression = "SET #u = :u, #ttl = :ttl ADD #rc :n";
        if (!ids.isEmpty()) {
            names.put("#aids", "astronautIds");
            values.put(":ids", AttributeValue.builder().ns(ids).build());
            expression += ", #aids :ids";
        }
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(table)
                .key(DynamoItemMapper.key(meta.reportId(), META_SK))
                .updateExpression(expression)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .build());
    }

    @Override
    public void addComments(ReportMeta meta, List<ReportComment> comments) {
        if (comments.isEmpty()) {
            return;
        }
        // misma clave para la misma notificación: PutItem es idempotente, no se duplica
        batchPut(comments.stream().map(c -> DynamoItemMapper.commentItem(meta, c)).toList());
        List<String> ids = comments.stream()
                .map(ReportComment::notificationId).filter(java.util.Objects::nonNull)
                .distinct().map(String::valueOf).toList();
        if (ids.isEmpty()) {
            return;
        }
        dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(table)
                .key(DynamoItemMapper.key(meta.reportId(), META_SK))
                .updateExpression("ADD #cids :ids")
                .expressionAttributeNames(Map.of("#cids", "commentIds"))
                .expressionAttributeValues(Map.of(":ids", AttributeValue.builder().ns(ids).build()))
                .build());
    }

    @Override
    public Optional<ReportDocument> load(String reportId) {
        ReportMeta meta = null;
        List<AstronautReading> readings = new ArrayList<>();
        List<ReportComment> comments = new ArrayList<>();
        for (Map<String, AttributeValue> item : queryAll(reportId)) {
            String sk = DynamoItemMapper.str(item, SK);
            if (META_SK.equals(sk)) {
                meta = DynamoItemMapper.metaFrom(item);
            } else if (sk.startsWith(READING_PREFIX)) {
                readings.add(DynamoItemMapper.readingFrom(item));
            } else if (sk.startsWith(COMMENT_PREFIX)) {
                comments.add(DynamoItemMapper.commentFrom(item));
            }
        }
        if (meta == null) {
            return Optional.empty();
        }
        readings.sort(Comparator.comparing(AstronautReading::capturedAt));
        comments.sort(Comparator.comparing(ReportComment::time));
        return Optional.of(new ReportDocument(meta, readings, comments));
    }

    @Override
    public List<ReportMeta> list() {
        List<ReportMeta> result = new ArrayList<>();
        dynamo.scanPaginator(ScanRequest.builder()
                        .tableName(table)
                        .filterExpression("#sk = :meta")
                        .expressionAttributeNames(Map.of("#sk", SK))
                        .expressionAttributeValues(Map.of(":meta", s(META_SK)))
                        .build())
                .items()
                .forEach(item -> result.add(DynamoItemMapper.metaFrom(item)));
        return result;
    }

    @Override
    public boolean delete(String reportId) {
        List<Map<String, AttributeValue>> items = queryAll(reportId);
        if (items.isEmpty()) {
            return false;
        }
        List<WriteRequest> deletes = items.stream()
                .map(i -> WriteRequest.builder().deleteRequest(DeleteRequest.builder()
                        .key(Map.of(PK, i.get(PK), SK, i.get(SK))).build()).build())
                .toList();
        batchWrite(deletes);
        return true;
    }

    // -------------------------------------------------------------- helpers

    private List<Map<String, AttributeValue>> queryAll(String reportId) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        dynamo.queryPaginator(QueryRequest.builder()
                        .tableName(table)
                        .keyConditionExpression("#pk = :pk")
                        .expressionAttributeNames(Map.of("#pk", PK))
                        .expressionAttributeValues(Map.of(":pk", s(DynamoItemMapper.pk(reportId))))
                        .build())
                .items()
                .forEach(items::add);
        return items;
    }

    private void batchPut(List<Map<String, AttributeValue>> items) {
        batchWrite(items.stream()
                .map(i -> WriteRequest.builder().putRequest(PutRequest.builder().item(i).build()).build())
                .toList());
    }

    /** BatchWriteItem en grupos de 25, reintentando lo que DynamoDB no procese. */
    private void batchWrite(List<WriteRequest> requests) {
        for (int i = 0; i < requests.size(); i += BATCH_SIZE) {
            Map<String, List<WriteRequest>> pending =
                    Map.of(table, requests.subList(i, Math.min(i + BATCH_SIZE, requests.size())));
            int attempts = 0;
            while (!pending.isEmpty() && attempts++ < 5) {
                Map<String, List<WriteRequest>> batch = pending;
                BatchWriteItemResponse response = dynamo.batchWriteItem(r -> r.requestItems(batch));
                pending = response.hasUnprocessedItems() ? response.unprocessedItems() : Map.of();
                if (!pending.isEmpty()) {
                    sleep(100L * attempts);
                }
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
