package com.ssolab.auth.passwordless.recovery;

import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoveryCodeService {

    private static final int CODE_COUNT = 10;

    private final RecoveryCodeRepository repository;
    private final UserIdentityRepository userRepository;
    private final RecoveryCodeGenerator generator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public RecoveryCodeService(
        RecoveryCodeRepository repository,
        UserIdentityRepository userRepository,
        RecoveryCodeGenerator generator,
        Clock clock
    ) {
        this(
            repository,
            userRepository,
            generator,
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
            clock
        );
    }

    RecoveryCodeService(
        RecoveryCodeRepository repository,
        UserIdentityRepository userRepository,
        RecoveryCodeGenerator generator,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.generator = generator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public RecoveryCodeBatch regenerate(UUID userId) {
        UserIdentityEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("identity is unavailable"));
        Instant now = clock.instant();
        List<RecoveryCodeEntity> existing = repository.findAllForUpdateByUserId(userId);
        existing.stream()
            .filter(code -> code.getUsedAt() == null && code.getInvalidatedAt() == null)
            .forEach(code -> code.invalidate(now));

        UUID batchId = UUID.randomUUID();
        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<RecoveryCodeEntity> newEntities = new ArrayList<>(CODE_COUNT);
        for (int index = 0; index < CODE_COUNT; index++) {
            String plaintext = generator.generate();
            plaintextCodes.add(plaintext);
            newEntities.add(RecoveryCodeEntity.create(
                UUID.randomUUID(),
                user,
                batchId,
                passwordEncoder.encode(normalize(plaintext)),
                now
            ));
        }
        repository.saveAll(existing);
        repository.saveAll(newEntities);
        repository.flush();
        return new RecoveryCodeBatch(plaintextCodes);
    }

    @Transactional
    public boolean consume(UUID userId, String candidate) {
        String normalized = normalize(candidate);
        List<RecoveryCodeEntity> codes = repository.findAllForUpdateByUserId(userId);
        RecoveryCodeEntity matched = null;
        for (RecoveryCodeEntity code : codes) {
            if (code.getUsedAt() == null && code.getInvalidatedAt() == null
                && passwordEncoder.matches(normalized, code.getCodeHash())) {
                matched = code;
                break;
            }
        }
        if (matched == null) {
            return false;
        }
        matched.use(clock.instant());
        repository.saveAndFlush(matched);
        return true;
    }

    @Transactional
    public void invalidateAll(UUID userId) {
        Instant now = clock.instant();
        List<RecoveryCodeEntity> codes = repository.findAllForUpdateByUserId(userId);
        codes.stream()
            .filter(code -> code.getInvalidatedAt() == null)
            .forEach(code -> code.invalidate(now));
        repository.saveAllAndFlush(codes);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "INVALID";
        }
        return value.replace("-", "").trim().toUpperCase(Locale.ROOT);
    }
}
