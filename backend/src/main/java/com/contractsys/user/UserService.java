package com.contractsys.user;

import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.common.PageRequests;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.user.dto.AssignRolesRequest;
import com.contractsys.user.dto.UserCreateRequest;
import com.contractsys.user.dto.UserStatusRequest;
import com.contractsys.user.dto.UserUpdateRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public Page<UserView> list(String keyword, int page, int size) {
        var pr = PageRequests.of(page, size).withSort(Sort.by("createdAt").descending());
        Page<SysUser> result = keyword == null || keyword.isEmpty()
                ? userRepository.findByDeletedFalse(pr)
                : userRepository.findByDeletedFalseAndUsernameContainingIgnoreCase(keyword, pr);
        return result.map(UserView::from);
    }

    public UserView get(Long id) {
        return UserView.from(findActiveUser(id));
    }

    public List<UserView> assignableUsers() {
        return userRepository.findByDeletedFalse(org.springframework.data.domain.PageRequest.of(0, 500, Sort.by("username").ascending()))
                .getContent()
                .stream()
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .map(UserView::from)
                .toList();
    }

    @Transactional
    public UserView create(UserCreateRequest request, SysUser operator) {
        if (userRepository.existsByUsernameAndDeletedFalse(request.username())) {
            throw ApiException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setEmail(request.email());
        List<Long> requestedRoleIds = request.effectiveRoleIds();
        if (requestedRoleIds != null) {
            if (requestedRoleIds.size() > 1) {
                throw ApiException.conflict("账号只能分配一个角色");
            }
            requestedRoleIds.forEach(roleId -> user.getRoles().add(findRole(roleId)));
        }
        if (user.getRoles().isEmpty()) {
            roleRepository.findByRoleCode("ROLE_NEW_USER").ifPresent(user.getRoles()::add);
        }
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "新增用户", "USER", user.getId(), user.getUsername()));
        return UserView.from(user);
    }

    @Transactional
    public UserView update(Long id, UserUpdateRequest request, SysUser operator) {
        SysUser user = findActiveUser(id);
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.email() != null) user.setEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 6) {
                throw ApiException.badRequest("密码长度不能少于6位");
            }
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "修改用户", "USER", user.getId(), user.getUsername()));
        return UserView.from(user);
    }

    @Transactional
    public void toggleStatus(Long id, UserStatusRequest request, SysUser operator) {
        SysUser user = findActiveUser(id);
        if ("admin".equals(user.getUsername()) && request.status() == UserStatus.DISABLED) {
            throw ApiException.conflict("内置管理员不能禁用");
        }
        user.setStatus(request.status());
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "启停用户", "USER", user.getId(),
                user.getUsername() + " -> " + request.status()));
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        SysUser user = findActiveUser(id);
        if ("admin".equals(user.getUsername())) {
            throw ApiException.conflict("内置管理员不能删除");
        }
        user.setDeleted(true);
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "删除用户", "USER", user.getId(), user.getUsername()));
    }

    @Transactional
    public UserView assignRoles(Long id, AssignRolesRequest request, SysUser operator) {
        SysUser user = findActiveUser(id);
        List<Long> requestedRoleIds = request.effectiveRoleIds();
        if ("admin".equals(user.getUsername()) && (requestedRoleIds == null || requestedRoleIds.isEmpty())) {
            throw ApiException.conflict("内置管理员至少需要保留一个角色");
        }
        if (requestedRoleIds != null && requestedRoleIds.size() > 1) {
            throw ApiException.conflict("账号只能分配一个角色");
        }
        user.getRoles().clear();
        if (requestedRoleIds != null) {
            requestedRoleIds.forEach(roleId -> user.getRoles().add(findRole(roleId)));
        }
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "分配角色", "USER", user.getId(), user.getUsername()));
        return UserView.from(user);
    }

    private SysUser findActiveUser(Long id) {
        return userRepository.findById(id).filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
    }

    private SysRole findRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("角色不存在: " + roleId));
    }
}
