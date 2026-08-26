package com.ssolab.hr.logout;

import jakarta.servlet.http.HttpSession;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class BffSessionRegistry {
    private record Entry(HttpSession session, String sid, String subject) {}
    private final ConcurrentHashMap<String, Entry> sessions = new ConcurrentHashMap<>();

    public void register(HttpSession session, OidcUser user) {
        String sid = user.getClaimAsString("sid");
        if (sid == null || sid.isBlank()) throw new IllegalStateException("OIDC sid is required");
        sessions.put(session.getId(), new Entry(session, sid, user.getSubject()));
    }

    public int invalidate(String sid, String subject) {
        Set<String> ids = ConcurrentHashMap.newKeySet();
        sessions.forEach((id, entry) -> {
            if ((sid != null && sid.equals(entry.sid()))
                || (sid == null && subject != null && subject.equals(entry.subject()))) ids.add(id);
        });
        ids.forEach(id -> {
            Entry entry = sessions.remove(id);
            if (entry != null) try { entry.session().invalidate(); } catch (IllegalStateException ignored) { }
        });
        return ids.size();
    }
}
