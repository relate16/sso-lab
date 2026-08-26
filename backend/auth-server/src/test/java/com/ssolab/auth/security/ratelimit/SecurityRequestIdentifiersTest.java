package com.ssolab.auth.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ssolab.auth.identity.crypto.EmailLookupHasher;
import com.ssolab.auth.identity.crypto.EmailNormalizer;
import com.ssolab.auth.identity.service.IdentityInputNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityRequestIdentifiersTest {

    private final SecurityRequestIdentifiers identifiers = new SecurityRequestIdentifiers(
        mock(IdentityInputNormalizer.class),
        mock(EmailNormalizer.class),
        mock(EmailLookupHasher.class)
    );

    @Test
    void usesContainerResolvedRemoteAddressInsteadOfClientSuppliedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.42");
        request.addHeader("Forwarded", "for=203.0.113.10;proto=https");
        request.addHeader("X-Forwarded-For", "203.0.113.11");
        request.addHeader("X-Real-IP", "203.0.113.12");

        assertThat(identifiers.remoteAddress(request)).isEqualTo("198.51.100.42");
    }
}
