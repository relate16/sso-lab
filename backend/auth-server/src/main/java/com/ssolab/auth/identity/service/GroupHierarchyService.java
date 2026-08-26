package com.ssolab.auth.identity.service;

import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class GroupHierarchyService {

    private final IdentityGroupRepository groupRepository;
    private final UserIdentityRepository userRepository;
    private final IdentityInputNormalizer inputNormalizer;
    private final Clock clock;

    public GroupHierarchyService(
        IdentityGroupRepository groupRepository,
        UserIdentityRepository userRepository,
        IdentityInputNormalizer inputNormalizer,
        Clock clock
    ) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.inputNormalizer = inputNormalizer;
        this.clock = clock;
    }

    @Transactional
    public IdentityGroupEntity createGroup(
        @NotBlank @Size(max = 100) String name,
        UUID parentId
    ) {
        String displayName = inputNormalizer.normalizeGroupName(name);
        String normalizedName = inputNormalizer.groupUniquenessKey(displayName);
        IdentityGroupEntity parent = parentId == null ? null : requireGroup(parentId);

        if (siblingNameExists(parentId, normalizedName)) {
            throw new IdentityConflictException("a group with the same name already exists under this parent");
        }

        Instant now = clock.instant();
        IdentityGroupEntity group = IdentityGroupEntity.create(
            UUID.randomUUID(),
            displayName,
            normalizedName,
            parent,
            now
        );
        try {
            return groupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException(
                "a group with the same name already exists under this parent",
                exception
            );
        }
    }

    @Transactional
    public void moveGroup(UUID groupId, UUID newParentId) {
        IdentityGroupEntity group = requireGroup(groupId);
        IdentityGroupEntity newParent = newParentId == null ? null : requireGroup(newParentId);

        if (groupId.equals(newParentId)) {
            throw new InvalidGroupHierarchyException("a group cannot be its own parent");
        }
        assertNotDescendant(groupId, newParent);
        UUID currentParentId = group.getParent() == null ? null : group.getParent().getId();
        if (Objects.equals(currentParentId, newParentId)) {
            return;
        }
        if (siblingNameExists(newParentId, group.getNormalizedName())) {
            throw new IdentityConflictException("a group with the same name already exists under the target parent");
        }
        group.moveTo(newParent, clock.instant());
        groupRepository.saveAndFlush(group);
    }

    @Transactional
    public void renameGroup(UUID groupId, @NotBlank @Size(max = 100) String name) {
        IdentityGroupEntity group = requireGroup(groupId);
        String displayName = inputNormalizer.normalizeGroupName(name);
        String normalizedName = inputNormalizer.groupUniquenessKey(displayName);
        UUID parentId = group.getParent() == null ? null : group.getParent().getId();
        if (!group.getNormalizedName().equals(normalizedName)
            && siblingNameExists(parentId, normalizedName)) {
            throw new IdentityConflictException(
                "a group with the same name already exists under this parent"
            );
        }
        group.rename(displayName, normalizedName, clock.instant());
        groupRepository.saveAndFlush(group);
    }

    @Transactional
    public void deleteGroup(UUID groupId) {
        IdentityGroupEntity group = requireGroup(groupId);
        if (groupRepository.existsByParentId(groupId)) {
            throw new IdentityConflictException("group cannot be deleted while child groups exist");
        }
        if (userRepository.countMembersOfGroup(groupId) > 0) {
            throw new IdentityConflictException("group cannot be deleted while members exist");
        }
        groupRepository.delete(group);
        groupRepository.flush();
    }

    @Transactional(readOnly = true)
    public String fullPath(UUID groupId) {
        IdentityGroupEntity current = requireGroup(groupId);
        Deque<String> segments = new ArrayDeque<>();
        while (current != null) {
            segments.addFirst(current.getName());
            current = current.getParent();
        }
        return "/" + String.join("/", segments);
    }

    private void assertNotDescendant(UUID groupId, IdentityGroupEntity candidateParent) {
        IdentityGroupEntity current = candidateParent;
        while (current != null) {
            if (groupId.equals(current.getId())) {
                throw new InvalidGroupHierarchyException("a descendant cannot become the parent of its ancestor");
            }
            current = current.getParent();
        }
    }

    private boolean siblingNameExists(UUID parentId, String normalizedName) {
        if (parentId == null) {
            return groupRepository.existsByParentIsNullAndNormalizedName(normalizedName);
        }
        return groupRepository.existsByParentIdAndNormalizedName(parentId, normalizedName);
    }

    private IdentityGroupEntity requireGroup(UUID groupId) {
        return groupRepository.findById(groupId)
            .orElseThrow(() -> new IdentityNotFoundException("group was not found"));
    }
}
