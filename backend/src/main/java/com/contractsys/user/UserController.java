package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.common.PageResponse;
import com.contractsys.common.PageRequests;
import com.contractsys.user.dto.AssignRolesRequest;
import com.contractsys.user.dto.UserCreateRequest;
import com.contractsys.user.dto.UserStatusRequest;
import com.contractsys.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequirePermission("user:manage")
public class UserController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserController(UserRepository userRepository, RoleRepository roleRepository,
                          AuthService authService, PasswordEncoder passwordEncoder,
                          ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserView>> list(@RequestParam(defaultValue = "") String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        authService.requireUser();
        var pr = PageRequests.of(page, size).withSort(Sort.by("createdAt").descending());
        Page<SysUser> result;
        if (keyword.isEmpty()) {
            result = userRepository.findByDeletedFalse(pr);
        } else {
            result = userRepository.findByDeletedFalseAndUsernameContainingIgnoreCase(keyword, pr);
        }
        return ApiResponse.ok(PageResponse.from(result.map(UserView::from)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserView> get(@PathVariable Long id) {
        authService.requireUser();
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        return ApiResponse.ok(UserView.from(user));
    }

    @GetMapping("/assignable")
    @RequirePermission({"user:manage", "contract:assign"})
    public ApiResponse<List<UserView>> assignableUsers() {
        authService.requireUser();
        return ApiResponse.ok(userRepository.findByDeletedFalse(org.springframework.data.domain.PageRequest.of(0, 500, Sort.by("username").ascending()))
                .getContent()
                .stream()
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .map(UserView::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest request) {
        SysUser operator = authService.requireUser();
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
        return ApiResponse.ok("创建成功", UserView.from(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id,
                                         @Valid @RequestBody UserUpdateRequest request) {
        SysUser operator = authService.requireUser();
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
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
        return ApiResponse.ok("更新成功", UserView.from(user));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id,
                                           @Valid @RequestBody UserStatusRequest request) {
        SysUser operator = authService.requireUser();
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("admin".equals(user.getUsername()) && request.status() == UserStatus.DISABLED) {
            throw ApiException.conflict("内置管理员不能禁用");
        }
        user.setStatus(request.status());
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "启停用户", "USER", user.getId(),
                user.getUsername() + " -> " + request.status()));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser operator = authService.requireUser();
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw ApiException.conflict("内置管理员不能删除");
        }
        user.setDeleted(true);
        userRepository.save(user);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "USER", "删除用户", "USER", user.getId(), user.getUsername()));
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserView> assignRoles(@PathVariable Long id,
                                              @Valid @RequestBody AssignRolesRequest request) {
        SysUser operator = authService.requireUser();
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
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
        return ApiResponse.ok("角色分配成功", UserView.from(user));
    }

    private SysRole findRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("角色不存在: " + roleId));
    }
}
