package com.contractsys.customer;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.common.PageResponse;
import com.contractsys.customer.dto.CustomerRequest;
import com.contractsys.user.SysUser;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
@RequirePermission("customer:manage")
public class CustomerController {
    private final CustomerService customerService;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerController(CustomerService customerService, AuthService authService,
                              ApplicationEventPublisher eventPublisher) {
        this.customerService = customerService;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
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
        eventPublisher.publishEvent(new OperationLogEvent(user, "CUSTOMER", "新增客户", "CUSTOMER", customer.getId(), customer.getName()));
        return ApiResponse.ok(customer);
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable Long id,
                                        @Valid @RequestBody CustomerRequest request) {
        SysUser user = authService.requireUser();
        Customer customer = customerService.update(id, request);
        eventPublisher.publishEvent(new OperationLogEvent(user, "CUSTOMER", "修改客户", "CUSTOMER", customer.getId(), customer.getName()));
        return ApiResponse.ok(customer);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        customerService.delete(id);
        eventPublisher.publishEvent(new OperationLogEvent(user, "CUSTOMER", "删除客户", "CUSTOMER", id, "删除客户"));
        return ApiResponse.ok(null);
    }
}
