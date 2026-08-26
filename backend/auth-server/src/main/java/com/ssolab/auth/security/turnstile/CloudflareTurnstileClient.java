package com.ssolab.auth.security.turnstile;

import java.util.Arrays;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class CloudflareTurnstileClient implements TurnstileClient {
    private final RestClient client;

    public CloudflareTurnstileClient(TurnstileProperties properties) {
        this.client = RestClient.builder().baseUrl(properties.verificationUri().toString()).build();
    }

    @Override
    public boolean verify(char[] secret, String responseToken, String remoteAddress) {
        char[] secretCopy = secret.clone();
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("secret", new String(secretCopy));
            form.add("response", responseToken);
            if (remoteAddress != null && !remoteAddress.isBlank()
                && !"unknown".equals(remoteAddress)) {
                form.add("remoteip", remoteAddress);
            }
            Map<?, ?> result = client.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } finally {
            Arrays.fill(secretCopy, '\0');
        }
    }
}
