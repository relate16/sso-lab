package com.ssolab.auth.passwordless.session;

import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.systemconfig.TypedSystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordlessSessionAuthenticationService {

    private final UserIdentityRepository userRepository;
    private final AuthSessionService authSessionService;
    private final TypedSystemConfigService configService;

    public PasswordlessSessionAuthenticationService(
        UserIdentityRepository userRepository,
        AuthSessionService authSessionService,
        TypedSystemConfigService configService
    ) {
        this.userRepository = userRepository;
        this.authSessionService = authSessionService;
        this.configService = configService;
    }

    @Transactional
    public PasswordlessPrincipal establish(
        HttpServletRequest request,
        UUID userId,
        AuthenticationMethod method,
        SessionScope scope
    ) {
        UserIdentityEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("identity is unavailable"));
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            authSessionService.invalidate(existing.getId());
            existing.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(Math.toIntExact(
            configService.identityPolicy().ssoSessionIdleTimeout().toSeconds()
        ));

        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .sorted()
            .toList();
        UserSessionMetadataEntity metadata = authSessionService.create(
            session.getId(), userId, method, scope, request.getHeader("User-Agent")
        );
        PasswordlessPrincipal principal = new PasswordlessPrincipal(
            user.getId(), user.getUserId(), roles, scope, method,
            metadata.getCreatedAt(), session.getId()
        );
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (scope == SessionScope.NORMAL) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            authorities.add(new SimpleGrantedAuthority("SCOPE_NORMAL"));
        } else {
            authorities.add(new SimpleGrantedAuthority("SCOPE_RECOVERY_ONLY"));
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, authorities
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            context
        );
        return principal;
    }

    public void invalidateCurrent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            authSessionService.invalidate(session.getId());
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
