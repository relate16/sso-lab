package com.ssolab.auth.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupHierarchyServiceTest {

    @Mock
    IdentityGroupRepository groupRepository;

    @Mock
    UserIdentityRepository userRepository;

    @Test
    void rejectsMovingGroupBelowItsDescendant() {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        IdentityGroupEntity root = IdentityGroupEntity.create(
            rootId, "회사", "회사", null, now
        );
        IdentityGroupEntity child = IdentityGroupEntity.create(
            childId, "개발본부", "개발본부", root, now
        );
        when(groupRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(groupRepository.findById(childId)).thenReturn(Optional.of(child));

        GroupHierarchyService service = new GroupHierarchyService(
            groupRepository,
            userRepository,
            new IdentityInputNormalizer(),
            Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> service.moveGroup(rootId, childId))
            .isInstanceOf(InvalidGroupHierarchyException.class)
            .hasMessageContaining("descendant");
    }
}
