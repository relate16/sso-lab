package com.ssolab.auth.oidc.flow;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Service;

@Service
public class OidcContinuationService {

    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public String consume(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest == null) {
            return null;
        }
        URI uri = URI.create(savedRequest.getRedirectUrl());
        if (!"/oauth2/authorize".equals(uri.getPath())) {
            return null;
        }
        String path = uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
