package com.contractsys.customer;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.customer.dto.CustomerRequest;
import com.contractsys.log.OperationLogService;
import com.contractsys.user.SysUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequirePermission("customer:manage")
public class CustomerController {
    private final CustomerService customerService;
    private final AuthService authService;
    private final OperationLogService operationLogService;

    public CustomerController(CustomerService customerService, AuthService authService,
                              OperationLogService operationLogService) {
        this.customerService = customerService;
        this.authService = authService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Customer>> list(@RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        authService.requireUser();
        return ApiResponse.ok(PageResponse.from(customerService.list(keyword, page, size)));
    }

    @PostMapping
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerRequest request) {
        SysUser user = authService.requireUser();
        Customer customer = customerService.create(request);
        operationLogService.record(user, "CUSTOMER", "新增客户", "CUSTOMER", customer.getId(), customer.getName());
        return ApiResponse.ok(customer);
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id,
                                        @Valid @RequestBody CustomerRequest request) {
        SysUser user = authService.requireUser();
        Customer customer = customerService.update(id, request);
        operationLogService.record(user, "CUSTOMER", "修改客户", "CUSTOMER", customer.getId(), customer.getName());
        return ApiResponse.ok(customer);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        customerService.delete(id);
        operationLogService.record(user, "CUSTOMER", "删除客户", "CUSTOMER", id, "删除客户");
        return ApiResponse.ok(null);
    }
}
