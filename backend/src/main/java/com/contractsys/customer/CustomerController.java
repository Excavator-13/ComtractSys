package com.contractsys.customer;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.customer.dto.CustomerRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequirePermission("customer:manage")
public class CustomerController {
    private final CustomerService customerService;
    private final AuthService authService;

    public CustomerController(CustomerService customerService, AuthService authService) {
        this.customerService = customerService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Customer>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        authService.requireUser(authorization);
        return ApiResponse.ok(PageResponse.from(customerService.list(keyword, page, size)));
    }

    @PostMapping
    public ApiResponse<Customer> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @Valid @RequestBody CustomerRequest request) {
        authService.requireUser(authorization);
        return ApiResponse.ok(customerService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id,
                                        @Valid @RequestBody CustomerRequest request) {
        authService.requireUser(authorization);
        return ApiResponse.ok(customerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable Long id) {
        authService.requireUser(authorization);
        customerService.delete(id);
        return ApiResponse.ok(null);
    }
}
