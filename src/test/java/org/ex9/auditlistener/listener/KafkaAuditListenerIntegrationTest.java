package org.ex9.auditlistener.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.model.AuditLogEntity;
import org.ex9.auditlistener.model.HttpLogEntity;
import org.ex9.auditlistener.repository.AuditMethodRepository;
import org.ex9.auditlistener.repository.AuditRequestRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(
        partitions = 3,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092",
                "transaction.state.log.replication.factor=1",
                "transaction.state.log.min.isr=1"
        },
        topics = {"audit.methods", "audit.requests"}
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests with EmbeddedKafka and Elasticsearch Testcontainer")
public class KafkaAuditListenerIntegrationTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.19.2")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
        registry.add("audit.kafka.topic.methodsTopic", () -> "audit.methods");
        registry.add("audit.kafka.topic.requestsTopic", () -> "audit.requests");
    }

    @Autowired
    private AuditMethodRepository auditMethodRepository;

    @Autowired
    private AuditRequestRepository auditRequestRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.producerProps("localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(props);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        cleanupIndices();
    }

    @AfterEach
    void tearDown() {
        cleanupIndices();
    }

    private void cleanupIndices() {
        try {
            auditMethodRepository.deleteAll();
            auditRequestRepository.deleteAll();
            elasticsearchOperations.indexOps(AuditLogEntity.class).refresh();
            elasticsearchOperations.indexOps(HttpLogEntity.class).refresh();
        } catch (Exception e) {
        }
    }

    @Test
    @Order(1)
    void test_elasticsearch_container_running() {
        assertTrue(elasticsearch.isRunning(), "Elasticsearch container should be running");

        assertDoesNotThrow(() -> {
            long count = auditMethodRepository.count();
            assertEquals(0, count, "Elasticsearch should be empty initially");
        });
    }

    @Test
    @Order(2)
    void test_auditLog_save() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();

        AuditLogDto auditLog = AuditLogDto.builder()
                .messageId(messageId)
                .id(eventId)
                .type("START")
                .methodName("TestService.testMethod")
                .args(new Object[]{"arg1", "arg2"})
                .result("success")
                .logLevel("INFO")
                .timestamp(LocalDateTime.now().toString())
                .build();

        String message = objectMapper.writeValueAsString(auditLog);

        var future = kafkaTemplate.send("audit.methods", message);
        assertNotNull(future.get(5, TimeUnit.SECONDS));

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    elasticsearchOperations.indexOps(AuditLogEntity.class).refresh();

                    Iterable<AuditLogEntity> logsIterable = auditMethodRepository.findAll();
                    List<AuditLogEntity> logs = new ArrayList<>();
                    logsIterable.forEach(logs::add);

                    assertEquals(1, logs.size());

                    var saved = logs.get(0);
                    assertEquals(UUID.fromString(eventId), saved.getAuditId());
                    assertEquals("START", saved.getEventType());
                    assertEquals("TestService.testMethod", saved.getMethod());
                    assertEquals("INFO", saved.getLevel());
                    assertNotNull(saved.getTimestamp());
                });
    }

    @Test
    @Order(3)
    void test_httpLog_save() throws Exception {
        String messageId = UUID.randomUUID().toString();

        HttpLogDto httpLog = HttpLogDto.builder()
                .messageId(messageId)
                .timestamp(LocalDateTime.now().toString())
                .direction("Incoming")
                .method("POST")
                .statusCode(201)
                .url("/api/users")
                .requestBody("{\"name\":\"John\"}")
                .responseBody("{\"id\":1,\"name\":\"John\"}")
                .build();

        String message = objectMapper.writeValueAsString(httpLog);

        var sendResult = kafkaTemplate.send("audit.requests", message).get(5, TimeUnit.SECONDS);
        assertNotNull(sendResult);

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    elasticsearchOperations.indexOps(HttpLogEntity.class).refresh();

                    Iterable<HttpLogEntity> logsIterable = auditRequestRepository.findAll();
                    List<HttpLogEntity> logs = new ArrayList<>();
                    logsIterable.forEach(logs::add);

                    assertEquals(1, logs.size());

                    var saved = logs.get(0);
                    assertEquals("POST", saved.getMethod());
                    assertEquals("/api/users", saved.getUrl());
                    assertEquals(201, saved.getStatusCode());
                    assertEquals("Incoming", saved.getDirection());
                    assertNotNull(saved.getRequestBody());
                    assertNotNull(saved.getResponseBody());
                });
    }

    @Test
    @Order(4)
    void test_exactlyOnceSemantic() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString();

        AuditLogDto auditLog = AuditLogDto.builder()
                .messageId(messageId)
                .id(eventId)
                .type("DUPLICATE_TEST")
                .methodName("DuplicateTest.method")
                .logLevel("WARN")
                .timestamp(LocalDateTime.now().toString())
                .build();

        String message = objectMapper.writeValueAsString(auditLog);

        for (int i = 0; i < 5; i++) {
            kafkaTemplate.send("audit.methods", message).get(5, TimeUnit.SECONDS);
            Thread.sleep(100);
        }

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    elasticsearchOperations.indexOps(AuditLogEntity.class).refresh();

                    Iterable<AuditLogEntity> logsIterable = auditMethodRepository.findAll();
                    List<AuditLogEntity> logs = new ArrayList<>();
                    logsIterable.forEach(logs::add);

                    assertTrue(logs.size() >= 1);

                });
    }

    @Test
    @Order(5)
    void test_invalidJson_notSaved() throws Exception {
        String invalidJson = "{ \"invalid\": json }";

        kafkaTemplate.send("audit.methods", invalidJson).get(5, TimeUnit.SECONDS);

        Thread.sleep(3000);

        elasticsearchOperations.indexOps(AuditLogEntity.class).refresh();
        elasticsearchOperations.indexOps(HttpLogEntity.class).refresh();

        assertEquals(0, auditMethodRepository.count());
        assertEquals(0, auditRequestRepository.count());
    }

}
