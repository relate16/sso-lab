package com.ssolab.auth.identity.service;

import com.ssolab.auth.identity.crypto.EmailCipher;
import com.ssolab.auth.identity.crypto.EmailLookupHasher;
import com.ssolab.auth.identity.crypto.EmailNormalizer;
import com.ssolab.auth.identity.crypto.EncryptedEmail;
import com.ssolab.auth.identity.model.RoleEntity;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.RoleRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class UserIdentityService {

    private final UserIdentityRepository userRepository;
    private final RoleRepository roleRepository;
    private final IdentityInputNormalizer inputNormalizer;
    private final EmailNormalizer emailNormalizer;
    private final EmailCipher emailCipher;
    private final EmailLookupHasher emailLookupHasher;
    private final Clock clock;

    public UserIdentityService(
        UserIdentityRepository userRepository,
        RoleRepository roleRepository,
        IdentityInputNormalizer inputNormalizer,
        EmailNormalizer emailNormalizer,
        EmailCipher emailCipher,
        EmailLookupHasher emailLookupHasher,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.inputNormalizer = inputNormalizer;
        this.emailNormalizer = emailNormalizer;
        this.emailCipher = emailCipher;
        this.emailLookupHasher = emailLookupHasher;
        this.clock = clock;
    }

    @Transactional
    public UserIdentityEntity createIdentity(@Valid CreateUserIdentityCommand command) {
        String normalizedUserId = inputNormalizer.normalizeUserId(command.userId());
        String username = inputNormalizer.normalizeUsername(command.username());
        String normalizedEmail = emailNormalizer.normalize(command.email());
        byte[] emailLookupHash = emailLookupHasher.hash(normalizedEmail);

        if (userRepository.existsByNormalizedUserId(normalizedUserId)) {
            throw new IdentityConflictException("userId is already registered");
        }
        if (userRepository.existsByEmailLookupHash(emailLookupHash)) {
            throw new IdentityConflictException("email is already registered");
        }

        EncryptedEmail encryptedEmail = emailCipher.encrypt(normalizedEmail);
        return createVerifiedIdentity(
            normalizedUserId,
            username,
            encryptedEmail,
            emailLookupHash
        );
    }

    @Transactional(noRollbackFor = IdentityConflictException.class)
    public UserIdentityEntity createVerifiedIdentity(
        String normalizedUserId,
        String username,
        EncryptedEmail encryptedEmail,
        byte[] emailLookupHash
    ) {
        if (userRepository.existsByNormalizedUserId(normalizedUserId)) {
            throw new IdentityConflictException("userId is already registered");
        }
        if (userRepository.existsByEmailLookupHash(emailLookupHash)) {
            throw new IdentityConflictException("email is already registered");
        }

        Instant now = clock.instant();
        UserIdentityEntity user = UserIdentityEntity.create(
            UUID.randomUUID(),
            normalizedUserId,
            username,
            encryptedEmail,
            emailLookupHash,
            now
        );
        RoleEntity userRole = roleRepository.findByName(RoleName.USER)
            .orElseThrow(() -> new IllegalStateException("seeded USER role is missing"));
        user.addRole(userRole);

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException("userId or email is already registered", exception);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentityEntity> findByEmail(String email) {
        String normalizedEmail = emailNormalizer.normalize(email);
        return userRepository.findByEmailLookupHash(emailLookupHasher.hash(normalizedEmail));
    }

    @Transactional(readOnly = true)
    public Optional<UserIdentityEntity> findByUserId(String userId) {
        return userRepository.findByNormalizedUserId(inputNormalizer.normalizeUserId(userId));
    }

    @Transactional(readOnly = true)
    public String decryptEmail(UserIdentityEntity user) {
        return emailCipher.decrypt(user.encryptedEmail());
    }
}
