package com.contractsys.auth;

import com.contractsys.auth.dto.LoginResponse;
import com.contractsys.auth.dto.RegisterRequest;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.user.*;
import com.contractsys.user.dto.UserUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider,
                       UserService userService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("两次输入的密码不一致");
        }
        freeDeletedUsernameOrFail(request.username());
        SysRole newUserRole = roleRepository.findByRoleCode("ROLE_NEW_USER")
                .orElseThrow(() -> ApiException.notFound("默认角色不存在"));
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank() ? request.username() : request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(newUserRole);
        try {
            return UserView.from(userRepository.save(user));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("用户名已存在");
        }
    }

    public LoginResponse login(String username, String password) {
        SysUser user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (user.getStatus() != UserStatus.ENABLED || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, UserView.from(user));
    }

    public SysUser requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw ApiException.unauthorized("请先登录");
        }
        if (auth.getPrincipal() instanceof SysUser user) {
            return user;
        }
        if (!(auth.getPrincipal() instanceof Long userId)) {
            throw ApiException.unauthorized("请先登录");
        }
        SysUser user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.unauthorized("登录用户不存在"));
        if (user.getStatus() != UserStatus.ENABLED) {
            throw ApiException.unauthorized("账号已禁用");
        }
        return user;
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    public UserView currentUser() {
        return UserView.from(requireUser());
    }

    public UserView updateProfile(SysUser user, UserUpdateRequest request) {
        return userService.update(user.getId(), request, user);
    }

    public void requireAnyPermission(SysUser user, Collection<String> requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.isEmpty()) {
            return;
        }
        boolean allowed = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> requiredPermissions.contains(permission.getPermissionCode()));
        if (!allowed) {
            throw ApiException.forbidden("无权限，需要权限: " + String.join(",", requiredPermissions));
        }
    }

    public boolean hasPermission(SysUser user, String permissionCode) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permission.getPermissionCode().equals(permissionCode));
    }

    private void freeDeletedUsernameOrFail(String username) {
        userRepository.findByUsername(username).ifPresent(existing -> {
            if (!existing.isDeleted()) {
                throw ApiException.conflict("用户名已存在");
            }
            existing.setUsername("deleted#" + existing.getId());
            userRepository.saveAndFlush(existing);
        });
    }
}
