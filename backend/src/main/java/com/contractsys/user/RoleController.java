package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuthService authService;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                          AuthService authService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<SysRole>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(roleRepository.findAll());
    }

    @PostMapping
    public ApiResponse<SysRole> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody Map<String, String> body) {
        authService.requireUser(authorization);
        String code = body.get("roleCode");
        String name = body.get("roleName");
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw ApiException.badRequest("角色编码和名称不能为空");
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(body.getOrDefault("description", ""));
        return ApiResponse.ok("创建成功", roleRepository.save(role));
    }

    @PutMapping("/{id}")
    public ApiResponse<SysRole> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        authService.requireUser(authorization);
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (body.containsKey("roleName")) role.setRoleName(body.get("roleName"));
        if (body.containsKey("description")) role.setDescription(body.get("description"));
        return ApiResponse.ok(roleRepository.save(role));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable Long id) {
        authService.requireUser(authorization);
        if (!roleRepository.existsById(id)) throw ApiException.notFound("角色不存在");
        roleRepository.deleteById(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<SysRole> assignPermissions(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        authService.requireUser(authorization);
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        role.getPermissions().clear();
        @SuppressWarnings("unchecked")
        var permIds = (java.util.List<Number>) body.get("permissionIds");
        if (permIds != null) {
            for (Number pid : permIds) {
                permissionRepository.findById(pid.longValue()).ifPresent(role.getPermissions()::add);
            }
        }
        return ApiResponse.ok(roleRepository.save(role));
    }
}
