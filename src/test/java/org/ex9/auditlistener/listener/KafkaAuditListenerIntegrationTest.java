package org.ex9.auditlistener.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.repository.AuditLogRepository;
import org.ex9.auditlistener.repository.HttpLogRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
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
        topics = {"audit-log"}
)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests with EmbeddedKafka and PostgreSQL Testcontainer")
public class KafkaAuditListenerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:latest"))
            .withDatabaseName("auditdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
        registry.add("audit.kafka.topic", () -> "audit-log");
    }

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private HttpLogRepository httpLogRepository;

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
        kafkaTemplate.setDefaultTopic("audit-log");
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        httpLogRepository.deleteAll();
    }

    @Test
    @Order(1)
    void test_postgres_container_running() {
        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");

        assertDoesNotThrow(() -> {
            long count = auditLogRepository.count();
            assertEquals(0, count, "Database should be empty initially");
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

        var future = kafkaTemplate.send("audit-log", message);
        assertNotNull(future.get(5, TimeUnit.SECONDS));

        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    var logs = auditLogRepository.findAll();
                    assertEquals(1, logs.size());

                    var saved = logs.get(0);
                    assertEquals(eventId, saved.getEventId());
                    assertEquals("START", saved.getEventType());
                    assertEquals("TestService.testMethod", saved.getMethodName());
                    assertEquals("INFO", saved.getLogLevel());
                    assertNotNull(saved.getKafkaTopic());
                    assertNotNull(saved.getKafkaPartition());
                    assertNotNull(saved.getKafkaOffset());
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

        var sendResult = kafkaTemplate.send("audit-log", message).get(5, TimeUnit.SECONDS);
        assertNotNull(sendResult);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var logs = httpLogRepository.findAll();
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
        // Given
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
            kafkaTemplate.send("audit-log", message).get(5, TimeUnit.SECONDS);
            Thread.sleep(100); // Small delay between sends
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var logs = auditLogRepository.findAll();
                    assertEquals(1, logs.size());
                    assertEquals(eventId, logs.get(0).getEventId());
                });
    }

    @Test
    @Order(5)
    void test_invalidJson_notSaved() throws Exception {
        String invalidJson = "{ \"invalid\": json }";

        kafkaTemplate.send("audit-log", invalidJson).get(5, TimeUnit.SECONDS);

        Thread.sleep(3000);

        assertEquals(0, auditLogRepository.count());
        assertEquals(0, httpLogRepository.count());
    }

}
