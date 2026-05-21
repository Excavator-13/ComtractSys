package com.contractsys.auth;

import com.contractsys.auth.dto.LoginResponse;
import com.contractsys.auth.dto.RegisterRequest;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.user.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TokenInfo> tokenUserIds = new ConcurrentHashMap<>();

    private record TokenInfo(Long userId, Instant expiresAt) {}

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw ApiException.badRequest("两次输入的密码不一致");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(request.username())) {
            throw ApiException.conflict("用户名已存在");
        }
        SysRole newUserRole = roleRepository.findByRoleCode("ROLE_NEW_USER")
                .orElseThrow(() -> ApiException.notFound("默认角色不存在"));
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setDisplayName(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(newUserRole);
        return UserView.from(userRepository.save(user));
    }

    public LoginResponse login(String username, String password) {
        SysUser user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> ApiException.unauthorized("用户名或密码错误"));
        if (user.getStatus() != UserStatus.ENABLED || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        String token = createToken();
        tokenUserIds.put(token, new TokenInfo(user.getId(), Instant.now().plusSeconds(86400)));
        return new LoginResponse(token, UserView.from(user));
    }

    public SysUser requireUser(String authorization) {
        String token = parseToken(authorization);
        TokenInfo info = tokenUserIds.get(token);
        if (info == null) {
            throw ApiException.unauthorized("请先登录");
        }
        if (Instant.now().isAfter(info.expiresAt)) {
            tokenUserIds.remove(token);
            throw ApiException.unauthorized("登录已过期，请重新登录");
        }
        return userRepository.findById(info.userId).orElseThrow(() -> ApiException.unauthorized("登录用户不存在"));
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            tokenUserIds.remove(authorization.substring("Bearer ".length()));
        }
    }

    public UserView currentUser(String authorization) {
        return UserView.from(requireUser(authorization));
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

    private String parseToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw ApiException.unauthorized("请先登录");
        }
        return authorization.substring("Bearer ".length());
    }

    private String createToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
