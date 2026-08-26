package com.ssolab.auth.oidc.logout;

import java.util.List;

record LogoutBatch(List<String> authSessionIds, List<BackChannelLogoutCommand> commands) {
    LogoutBatch {
        authSessionIds = List.copyOf(authSessionIds);
        commands = List.copyOf(commands);
    }
}
