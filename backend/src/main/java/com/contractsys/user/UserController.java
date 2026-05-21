package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.user.dto.AssignRolesRequest;
import com.contractsys.user.dto.UserCreateRequest;
import com.contractsys.user.dto.UserStatusRequest;
import com.contractsys.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
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

    public UserController(UserRepository userRepository, RoleRepository roleRepository,
                          AuthService authService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserView>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @RequestParam(defaultValue = "") String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        authService.requireUser(authorization);
        PageRequest pr = PageRequest.of(Math.max(page - 1, 0), size, Sort.by("createdAt").descending());
        Page<SysUser> result;
        if (keyword.isEmpty()) {
            result = userRepository.findByDeletedFalse(pr);
        } else {
            result = userRepository.findByDeletedFalseAndUsernameContainingIgnoreCase(keyword, pr);
        }
        return ApiResponse.ok(PageResponse.from(result.map(UserView::from)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserView> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable Long id) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        return ApiResponse.ok(UserView.from(user));
    }

    @GetMapping("/assignable")
    @RequirePermission({"user:manage", "contract:assign"})
    public ApiResponse<List<UserView>> assignableUsers(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(userRepository.findByDeletedFalse(PageRequest.of(0, 500, Sort.by("username").ascending()))
                .getContent()
                .stream()
                .filter(user -> user.getStatus() == UserStatus.ENABLED)
                .map(UserView::from)
                .toList());
    }

    @PostMapping
    public ApiResponse<UserView> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @Valid @RequestBody UserCreateRequest request) {
        authService.requireUser(authorization);
        if (userRepository.existsByUsernameAndDeletedFalse(request.username())) {
            throw ApiException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setEmail(request.email());
        if (request.roleIds() != null) {
            request.roleIds().forEach(roleId -> roleRepository.findById(roleId).ifPresent(user.getRoles()::add));
        }
        if (user.getRoles().isEmpty()) {
            roleRepository.findByRoleCode("ROLE_NEW_USER").ifPresent(user.getRoles()::add);
        }
        userRepository.save(user);
        return ApiResponse.ok("创建成功", UserView.from(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable Long id,
                                         @Valid @RequestBody UserUpdateRequest request) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.email() != null) user.setEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        userRepository.save(user);
        return ApiResponse.ok("更新成功", UserView.from(user));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable Long id,
                                           @Valid @RequestBody UserStatusRequest request) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("admin".equals(user.getUsername()) && request.status() == UserStatus.DISABLED) {
            throw ApiException.conflict("内置管理员不能禁用");
        }
        user.setStatus(request.status());
        userRepository.save(user);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable Long id) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw ApiException.conflict("内置管理员不能删除");
        }
        user.setDeleted(true);
        userRepository.save(user);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserView> assignRoles(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id,
                                              @Valid @RequestBody AssignRolesRequest request) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if ("admin".equals(user.getUsername()) && (request.roleIds() == null || request.roleIds().isEmpty())) {
            throw ApiException.conflict("内置管理员至少需要保留一个角色");
        }
        user.getRoles().clear();
        if (request.roleIds() != null) {
            request.roleIds().forEach(roleId -> roleRepository.findById(roleId).ifPresent(user.getRoles()::add));
        }
        userRepository.save(user);
        return ApiResponse.ok("角色分配成功", UserView.from(user));
    }
}
