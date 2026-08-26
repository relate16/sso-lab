package com.ssolab.auth.oidc.logout;

import java.net.URI;

record BackChannelLogoutCommand(String clientId, URI endpoint, String subject, String sid) {
}
