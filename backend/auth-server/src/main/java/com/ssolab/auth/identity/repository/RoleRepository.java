package com.ssolab.auth.identity.repository;

import com.ssolab.auth.identity.model.RoleEntity;
import com.ssolab.auth.identity.model.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByName(RoleName name);
}
