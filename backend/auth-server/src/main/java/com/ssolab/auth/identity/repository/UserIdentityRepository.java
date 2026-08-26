package com.ssolab.auth.identity.repository;

import com.ssolab.auth.identity.model.AccountStatus;
import com.ssolab.auth.identity.model.RoleName;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserIdentity user where user.id = :userId")
    Optional<UserIdentityEntity> findLockedById(@Param("userId") UUID userId);

    boolean existsByNormalizedUserId(String normalizedUserId);

    boolean existsByEmailLookupHash(byte[] emailLookupHash);

    Optional<UserIdentityEntity> findByEmailLookupHash(byte[] emailLookupHash);

    Optional<UserIdentityEntity> findByNormalizedUserId(String normalizedUserId);

    @Query("select count(distinct user) from UserIdentity user join user.groups identityGroup " +
        "where identityGroup.id = :groupId")
    long countMembersOfGroup(@Param("groupId") UUID groupId);

    @Query("select count(distinct user) from UserIdentity user join user.roles role " +
        "where role.name = :roleName and user.status = :status")
    long countByRoleAndStatus(
        @Param("roleName") RoleName roleName,
        @Param("status") AccountStatus status
    );
}
