package com.ssolab.auth.secret;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SecretProviderRegistry {

    private final Map<SecretProviderType, SecretProvider> providers;

    public SecretProviderRegistry(List<SecretProvider> providers) {
        EnumMap<SecretProviderType, SecretProvider> mappedProviders =
            new EnumMap<>(SecretProviderType.class);
        for (SecretProvider provider : providers) {
            SecretProvider previous = mappedProviders.put(provider.type(), provider);
            if (previous != null) {
                throw new IllegalStateException("duplicate SecretProvider for type " + provider.type());
            }
        }
        this.providers = Map.copyOf(mappedProviders);
    }

    public SecretValue getSecret(SecretReference reference) {
        SecretProvider provider = providers.get(reference.provider());
        if (provider == null) {
            throw new SecretUnavailableException(
                "no SecretProvider is registered for type " + reference.provider()
            );
        }
        return provider.getSecret(reference.reference());
    }
}
