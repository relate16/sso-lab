package com.ssolab.auth.admin.service;

import org.springframework.stereotype.Component;

@Component
public class AdminEmailMasker {

    public String mask(String email) {
        int separator = email.indexOf('@');
        if (separator <= 0 || separator == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(separator);
    }
}
