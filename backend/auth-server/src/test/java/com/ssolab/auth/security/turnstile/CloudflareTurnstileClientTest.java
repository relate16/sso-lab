package com.ssolab.auth.security.turnstile;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssolab.auth.secret.SecretProviderType;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CloudflareTurnstileClientTest {
    @Test
    void readsSuccessAndFailureWithoutUsingProductionService() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/siteverify", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean success = calls.getAndIncrement() == 0;
            byte[] body = ("{\"success\":" + success + "}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            TurnstileProperties properties = new TurnstileProperties(true,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify"),
                SecretProviderType.ENVIRONMENT, "TURNSTILE_SECRET_KEY");
            CloudflareTurnstileClient client = new CloudflareTurnstileClient(properties);

            assertThat(client.verify("test-secret".toCharArray(), "test-response", "127.0.0.1"))
                .isTrue();
            assertThat(client.verify("test-secret".toCharArray(), "bad-response", "127.0.0.1"))
                .isFalse();
        } finally {
            server.stop(0);
        }
    }
}
