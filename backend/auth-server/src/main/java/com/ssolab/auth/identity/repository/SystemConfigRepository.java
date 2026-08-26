package com.ssolab.auth.identity.repository;

import com.ssolab.auth.identity.model.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {
}
