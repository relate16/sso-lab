package com.ssolab.auth.oidc.claims;

import java.util.List;
import java.util.Map;

public record OidcIdentityClaims(Map<String, Object> values) {

    public OidcIdentityClaims {
        values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public List<String> roles() {
        return (List<String>) values.get("roles");
    }
}
