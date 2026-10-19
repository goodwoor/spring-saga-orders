package saga.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Стенд сквозного теста: три сервиса, свои базы и Kafka.
 *
 * <p>Сервисы сканируют один пакет {@code saga}. В одном процессе они подхватят
 * чужие бины, поэтому каждый запускается своим {@code java -jar}.
 * Базы и брокер — контейнеры этого прогона. Их адреса передаются в окружение,
 * чтобы сервис не сел на локальную базу разработчика.
 *
 * <p>Тест только создаёт заказ через {@code POST /order}. Дальше сервисы
 * общаются сами. Ответ не мгновенный: исходящие сообщения уходят в Kafka
 * после паузы поллера 50 мс.
 *
 * <p>Так проверяют только самые критичные сценарии: успешный заказ и обе
 * компенсации. Остальное живёт в пакете {@code saga.single} — там один сервис
 * и своя база, без запуска соседей.
 */
@Testcontainers
class SagaFlowStand {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static final Pattern ORDER_ID = Pattern.compile("\\{\\s*\"id\"\\s*:\\s*(\\d+)");

    private static final List<RunningApp> APPS = new ArrayList<>();

    @Container
    static PostgreSQLContainer orders = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("orders");

    @Container
    static PostgreSQLContainer inventory = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("inventory");

    @Container
    static PostgreSQLContainer payments = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("payments");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @BeforeAll
    static void startApps() throws Exception {
        Path logDir = Path.of(System.getProperty("user.dir"), "target");
        Files.createDirectories(logDir);
        Path java = javaBinary();
        APPS.add(start(java, "order", "../order/target/order-0.0.1-SNAPSHOT.jar", orders, "18083", logDir));
        APPS.add(start(java, "inventory", "../inventory/target/inventory-0.0.1-SNAPSHOT.jar", inventory, "18082", logDir));
        APPS.add(start(java, "payment", "../payment/target/payment-0.0.1-SNAPSHOT.jar", payments, "18084", logDir));

        await()
                .atMost(Duration.ofMinutes(3))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertAppsAlive();
                    assertEquals(200, status("http://localhost:18083/order"), "order");
                    assertEquals(200, status("http://localhost:18082/inventory"), "inventory");
                    assertEquals(200, status("http://localhost:18084/payment"), "payment");
                });
    }

    @AfterAll
    static void stopApps() throws InterruptedException {
        for (RunningApp app : APPS) {
            app.process.destroyForcibly();
        }
        for (RunningApp app : APPS) {
            app.process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    protected static String postOrder(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:18083/order"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> created = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode(), created.body());
        Matcher matcher = ORDER_ID.matcher(created.body());
        assertTrue(matcher.find(), created.body());
        return matcher.group(1);
    }

    protected static void awaitStatus(String orderId, String status) {
        Pattern statusPattern = Pattern.compile("\"status\"\\s*:\\s*\"" + Pattern.quote(status) + "\"");
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertAppsAlive();
                    String body = getBody("http://localhost:18083/order/" + orderId);
                    assertTrue(statusPattern.matcher(body).find(), body);
                });
    }

    protected static int itemAmount(long itemId) throws Exception {
        return queryInt(inventory, "SELECT amount FROM items WHERE id = ?", itemId);
    }

    protected static int failedPaymentCount(long orderId) throws Exception {
        return queryInt(
                payments,
                "SELECT COUNT(*) FROM payments WHERE order_id = ? AND status = 'FAILED'",
                orderId
        );
    }

    private static Path javaBinary() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path exe = bin.resolve("java.exe");
        Path java = bin.resolve("java");
        if (Files.isRegularFile(exe)) {
            return exe;
        }
        if (Files.isRegularFile(java)) {
            return java;
        }
        throw new IllegalStateException("java not found in " + bin);
    }

    private static RunningApp start(
            Path java,
            String name,
            String relativeJar,
            PostgreSQLContainer database,
            String port,
            Path logDir
    ) throws Exception {
        Path jar = Path.of(System.getProperty("user.dir")).resolve(relativeJar).normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Fat jar not found: " + jar);
        }
        Path log = logDir.resolve(name + ".log");
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-jar", jar.toString());
        builder.directory(Path.of(System.getProperty("user.dir")).toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.to(log.toFile()));
        builder.environment().put("SPRING_DOCKER_COMPOSE_ENABLED", "false");
        builder.environment().put("SPRING_DATASOURCE_URL", database.getJdbcUrl());
        builder.environment().put("SPRING_DATASOURCE_USERNAME", database.getUsername());
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", database.getPassword());
        builder.environment().put("SPRING_KAFKA_BOOTSTRAP_SERVERS", kafka.getBootstrapServers());
        builder.environment().put("SERVER_PORT", port);
        return new RunningApp(name, builder.start(), log);
    }

    private static void assertAppsAlive() {
        for (RunningApp app : APPS) {
            if (!app.process.isAlive()) {
                throw new IllegalStateException(app.name + " exited " + app.process.exitValue() + "\n" + tail(app.log));
            }
        }
    }

    private static int queryInt(PostgreSQLContainer database, String sql, long parameter) throws Exception {
        Class.forName("org.postgresql.Driver");
        try (Connection connection = DriverManager.getConnection(
                database.getJdbcUrl(),
                database.getUsername(),
                database.getPassword());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), sql);
                return rows.getInt(1);
            }
        }
    }

    private static int status(String url) {
        try {
            return send(url).statusCode();
        } catch (Exception exception) {
            return -1;
        }
    }

    private static String getBody(String url) {
        try {
            HttpResponse<String> response = send(url);
            if (response.statusCode() != 200) {
                return "";
            }
            return response.body();
        } catch (Exception exception) {
            return "";
        }
    }

    private static HttpResponse<String> send(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String tail(Path log) {
        try {
            if (!Files.isRegularFile(log)) {
                return "";
            }
            List<String> lines = Files.readAllLines(log);
            int from = Math.max(0, lines.size() - 80);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (Exception exception) {
            return exception.toString();
        }
    }

    private record RunningApp(String name, Process process, Path log) {
    }
}
