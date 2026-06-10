package com.contractsys.user;

import com.contractsys.auth.CurrentUser;
import com.contractsys.auth.RequirePermission;
import com.contractsys.auth.dto.UserView;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.user.dto.AssignRolesRequest;
import com.contractsys.user.dto.UserCreateRequest;
import com.contractsys.user.dto.UserStatusRequest;
import com.contractsys.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequirePermission("user:manage")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResponse<UserView>> list(@RequestParam(defaultValue = "") String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @CurrentUser SysUser user) {
        return ApiResponse.ok(PageResponse.from(userService.list(keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserView> get(@PathVariable Long id, @CurrentUser SysUser user) {
        return ApiResponse.ok(userService.get(id));
    }

    @GetMapping("/assignable")
    @RequirePermission({"user:manage", "contract:assign"})
    public ApiResponse<List<UserView>> assignableUsers(@CurrentUser SysUser user) {
        return ApiResponse.ok(userService.assignableUsers());
    }

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest request,
                                        @CurrentUser SysUser operator) {
        return ApiResponse.ok("创建成功", userService.create(request, operator));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id,
                                         @Valid @RequestBody UserUpdateRequest request,
                                         @CurrentUser SysUser operator) {
        return ApiResponse.ok("更新成功", userService.update(id, request, operator));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id,
                                           @Valid @RequestBody UserStatusRequest request,
                                           @CurrentUser SysUser operator) {
        userService.toggleStatus(id, request, operator);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUser SysUser operator) {
        userService.delete(id, operator);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserView> assignRoles(@PathVariable Long id,
                                              @Valid @RequestBody AssignRolesRequest request,
                                              @CurrentUser SysUser operator) {
        return ApiResponse.ok("角色分配成功", userService.assignRoles(id, request, operator));
    }
}
