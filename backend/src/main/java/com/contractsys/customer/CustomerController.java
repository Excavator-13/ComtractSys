package com.contractsys.customer;

import com.contractsys.auth.CurrentUser;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.customer.dto.CustomerRequest;
import com.contractsys.user.SysUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequirePermission("customer:manage")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Customer>> list(@RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @CurrentUser SysUser user) {
        return ApiResponse.ok(PageResponse.from(customerService.list(keyword, page, size)));
    }

    @PostMapping
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerRequest request, @CurrentUser SysUser user) {
        return ApiResponse.ok(customerService.create(request, user));
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id,
                                        @Valid @RequestBody CustomerRequest request,
                                        @CurrentUser SysUser user) {
        return ApiResponse.ok(customerService.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUser SysUser user) {
        customerService.delete(id, user);
        return ApiResponse.ok(null);
    }
}
