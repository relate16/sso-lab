package com.ssolab.auth.testsupport;

import com.ssolab.auth.passwordless.mail.CapturedOtpMail;
import com.ssolab.auth.passwordless.mail.InMemoryVerificationMailSender;
import com.ssolab.auth.passwordless.mail.OtpMailPurpose;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@Profile("test")
@ConditionalOnProperty(prefix = "sso.test-support", name = "enabled", havingValue = "true")
@RequestMapping("/test-support/v1")
public class TestSupportController {

    static final String API_KEY_HEADER = "X-Test-Support-Key";

    private final TestSupportProperties properties;
    private final InMemoryVerificationMailSender mailSender;
    private final AdjustableTestClock clock;

    public TestSupportController(
        TestSupportProperties properties,
        InMemoryVerificationMailSender mailSender,
        AdjustableTestClock clock
    ) {
        this.properties = properties;
        this.properties.requireValid();
        this.mailSender = mailSender;
        this.clock = clock;
    }

    @GetMapping("/mail/{challengeId}")
    ResponseEntity<MailView> mail(
        @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
        @PathVariable UUID challengeId,
        @RequestParam OtpMailPurpose purpose
    ) {
        requireAccess(apiKey);
        CapturedOtpMail mail = mailSender.messages().stream()
            .filter(candidate -> candidate.challengeId().equals(challengeId))
            .filter(candidate -> candidate.purpose() == purpose)
            .reduce((first, second) -> second)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        return noStore(new MailView(
            mail.challengeId(), mail.purpose(), mail.code(), mail.expiresAt()
        ));
    }

    @DeleteMapping("/mail")
    ResponseEntity<Void> clearMail(
        @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey
    ) {
        requireAccess(apiKey);
        mailSender.clear();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/clock")
    ResponseEntity<Map<String, Instant>> setClock(
        @RequestHeader(name = API_KEY_HEADER, required = false) String apiKey,
        @RequestBody ClockRequest request
    ) {
        requireAccess(apiKey);
        clock.set(request.instant());
        return noStore(Map.of("instant", clock.instant()));
    }

    private void requireAccess(String candidate) {
        byte[] expected = properties.apiKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate == null
            ? new byte[0]
            : candidate.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(NOT_FOUND);
        }
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    public record ClockRequest(Instant instant) {
    }

    public record MailView(
        UUID challengeId,
        OtpMailPurpose purpose,
        String code,
        Instant expiresAt
    ) {
    }
}
