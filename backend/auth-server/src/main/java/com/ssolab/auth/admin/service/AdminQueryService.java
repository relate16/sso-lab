package com.ssolab.auth.admin.service;

import com.ssolab.auth.audit.AuditEntity;
import com.ssolab.auth.audit.AuditService;
import com.ssolab.auth.identity.model.IdentityGroupEntity;
import com.ssolab.auth.identity.model.UserIdentityEntity;
import com.ssolab.auth.identity.repository.IdentityGroupRepository;
import com.ssolab.auth.identity.repository.UserIdentityRepository;
import com.ssolab.auth.identity.service.IdentityNotFoundException;
import com.ssolab.auth.identity.service.UserIdentityService;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminQueryService {

    private final AdminIdentityGuard guard;
    private final UserIdentityRepository userRepository;
    private final IdentityGroupRepository groupRepository;
    private final UserIdentityService identityService;
    private final AdminEmailMasker emailMasker;
    private final AuditService auditService;

    public AdminQueryService(
        AdminIdentityGuard guard,
        UserIdentityRepository userRepository,
        IdentityGroupRepository groupRepository,
        UserIdentityService identityService,
        AdminEmailMasker emailMasker,
        AuditService auditService
    ) {
        this.guard = guard;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.identityService = identityService;
        this.emailMasker = emailMasker;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResponse<AdminDtos.UserView> users(
        UUID actorId,
        int page,
        int size
    ) {
        guard.requireActiveAdmin(actorId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<UserIdentityEntity> users = userRepository.findAll(PageRequest.of(
            safePage, safeSize, Sort.by(Sort.Direction.ASC, "normalizedUserId")
        ));
        return new AdminDtos.PageResponse<>(
            users.stream().map(this::userView).toList(),
            safePage,
            safeSize,
            users.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserView user(UUID actorId, UUID userId) {
        guard.requireActiveAdmin(actorId);
        return userView(userRepository.findById(userId).orElseThrow(
            () -> new IdentityNotFoundException("user was not found")
        ));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.GroupView> groups(UUID actorId) {
        guard.requireActiveAdmin(actorId);
        return groupRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
            .map(this::groupView)
            .sorted(Comparator.comparing(AdminDtos.GroupView::fullPath))
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.PageResponse<AdminDtos.AuditView> audit(
        UUID actorId,
        int page,
        int size
    ) {
        guard.requireActiveAdmin(actorId);
        Page<AuditEntity> audits = auditService.findRecent(page, size);
        return new AdminDtos.PageResponse<>(
            audits.stream().map(this::auditView).toList(),
            audits.getNumber(), audits.getSize(), audits.getTotalElements()
        );
    }

    private AdminDtos.UserView userView(UserIdentityEntity user) {
        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name()).sorted().toList();
        List<AdminDtos.GroupMembership> groups = user.getGroups().stream()
            .map(group -> new AdminDtos.GroupMembership(
                group.getId(), group.getName(), fullPath(group)
            ))
            .sorted(Comparator.comparing(AdminDtos.GroupMembership::fullPath))
            .toList();
        String maskedEmail = emailMasker.mask(identityService.decryptEmail(user));
        return new AdminDtos.UserView(
            user.getId(), user.getUserId(), user.getUsername(), maskedEmail,
            user.getStatus().name(), roles, groups, user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    private AdminDtos.GroupView groupView(IdentityGroupEntity group) {
        UUID parentId = group.getParent() == null ? null : group.getParent().getId();
        return new AdminDtos.GroupView(
            group.getId(), group.getName(), parentId, fullPath(group),
            userRepository.countMembersOfGroup(group.getId()),
            group.getCreatedAt(), group.getUpdatedAt()
        );
    }

    private AdminDtos.AuditView auditView(AuditEntity audit) {
        return new AdminDtos.AuditView(
            audit.getId(), audit.getEvent().name(), audit.getActorId(), audit.getTargetId(),
            audit.isSuccess(), audit.getSource().name(), audit.getTraceId(), audit.getOccurredAt()
        );
    }

    private String fullPath(IdentityGroupEntity group) {
        ArrayDeque<String> segments = new ArrayDeque<>();
        IdentityGroupEntity current = group;
        int depth = 0;
        while (current != null) {
            if (++depth > 100) {
                throw new IllegalStateException("identity group hierarchy exceeds safe depth");
            }
            segments.addFirst(current.getName());
            current = current.getParent();
        }
        return "/" + String.join("/", segments);
    }
}
