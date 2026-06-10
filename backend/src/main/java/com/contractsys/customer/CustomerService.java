package com.contractsys.customer;

import com.contractsys.common.ApiException;
import com.contractsys.common.BusinessNumberService;
import com.contractsys.common.PageRequests;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.customer.dto.CustomerRequest;
import com.contractsys.user.SysUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final List<CustomerReferenceChecker> referenceCheckers;
    private final BusinessNumberService numberService;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerService(CustomerRepository customerRepository,
                           List<CustomerReferenceChecker> referenceCheckers,
                           BusinessNumberService numberService,
                           ApplicationEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.referenceCheckers = referenceCheckers;
        this.numberService = numberService;
        this.eventPublisher = eventPublisher;
    }

    public Page<Customer> list(String keyword, int page, int size) {
        return customerRepository.findByDeletedFalseAndNameContainingIgnoreCase(
                keyword == null ? "" : keyword,
                PageRequests.of(page, size)
        );
    }

    @Transactional
    public Customer create(CustomerRequest request, SysUser operator) {
        Customer customer = new Customer();
        customer.setCustomerNo(numberService.temporaryNumber());
        fill(customer, request);
        Customer saved = customerRepository.saveAndFlush(customer);
        saved.setCustomerNo(numberService.customerNo(saved.getId()));
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CUSTOMER", "新增客户", "CUSTOMER", saved.getId(), saved.getName()));
        return saved;
    }

    @Transactional
    public Customer update(Long id, CustomerRequest request, SysUser operator) {
        Customer customer = customerRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
        fill(customer, request);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CUSTOMER", "修改客户", "CUSTOMER", customer.getId(), customer.getName()));
        return customer;
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        Customer customer = customerRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
        for (CustomerReferenceChecker checker : referenceCheckers) {
            if (checker.hasReference(id)) {
                throw ApiException.conflict("客户已被" + checker.moduleName() + "引用，不能删除");
            }
        }
        customer.setDeleted(true);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CUSTOMER", "删除客户", "CUSTOMER", id, "删除客户"));
    }

    private void fill(Customer customer, CustomerRequest request) {
        customer.setName(request.name());
        customer.setTel(request.tel());
        customer.setAddress(request.address());
        customer.setFax(request.fax());
        customer.setPostalCode(request.postalCode());
        customer.setBankName(request.bankName());
        customer.setBankAccount(request.bankAccount());
        customer.setRemark(request.remark());
    }
}
