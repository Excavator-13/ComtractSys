package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
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

    @PostMapping
    public ApiResponse<UserView> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @RequestBody Map<String, Object> body) {
        authService.requireUser(authorization);
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("用户名和密码不能为空");
        }
        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            throw ApiException.conflict("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName((String) body.getOrDefault("displayName", username));
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPhone((String) body.get("phone"));
        user.setEmail((String) body.get("email"));
        userRepository.save(user);
        return ApiResponse.ok("创建成功", UserView.from(user));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable Long id,
                                         @RequestBody Map<String, Object> body) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        if (body.containsKey("displayName")) user.setDisplayName((String) body.get("displayName"));
        if (body.containsKey("phone")) user.setPhone((String) body.get("phone"));
        if (body.containsKey("email")) user.setEmail((String) body.get("email"));
        if (body.containsKey("password") && body.get("password") != null && !((String) body.get("password")).isBlank()) {
            user.setPasswordHash(passwordEncoder.encode((String) body.get("password")));
        }
        userRepository.save(user);
        return ApiResponse.ok("更新成功", UserView.from(user));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setStatus("DISABLED".equals(body.get("status")) ? UserStatus.DISABLED : UserStatus.ENABLED);
        userRepository.save(user);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable Long id) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.setDeleted(true);
        userRepository.save(user);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserView> assignRoles(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        authService.requireUser(authorization);
        SysUser user = userRepository.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在"));
        user.getRoles().clear();
        @SuppressWarnings("unchecked")
        var roleIds = (java.util.List<Number>) body.get("roleIds");
        if (roleIds != null) {
            for (Number rid : roleIds) {
                roleRepository.findById(rid.longValue()).ifPresent(user.getRoles()::add);
            }
        }
        userRepository.save(user);
        return ApiResponse.ok("角色分配成功", UserView.from(user));
    }
}
