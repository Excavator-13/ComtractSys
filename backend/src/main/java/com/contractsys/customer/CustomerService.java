package com.contractsys.customer;

import com.contractsys.common.ApiException;
import com.contractsys.common.BusinessNumberService;
import com.contractsys.common.PageRequests;
import com.contractsys.customer.dto.CustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final List<CustomerReferenceChecker> referenceCheckers;
    private final BusinessNumberService numberService;

    public CustomerService(CustomerRepository customerRepository,
                           List<CustomerReferenceChecker> referenceCheckers,
                           BusinessNumberService numberService) {
        this.customerRepository = customerRepository;
        this.referenceCheckers = referenceCheckers;
        this.numberService = numberService;
    }

    public Page<Customer> list(String keyword, int page, int size) {
        return customerRepository.findByDeletedFalseAndNameContainingIgnoreCase(
                keyword == null ? "" : keyword,
                PageRequests.of(page, size)
        );
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setCustomerNo(numberService.temporaryNumber());
        fill(customer, request);
        Customer saved = customerRepository.saveAndFlush(customer);
        saved.setCustomerNo(numberService.customerNo(saved.getId()));
        return saved;
    }

    @Transactional
    public Customer update(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
        fill(customer, request);
        return customer;
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = customerRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
        for (CustomerReferenceChecker checker : referenceCheckers) {
            if (checker.hasReference(id)) {
                throw ApiException.conflict("客户已被" + checker.moduleName() + "引用，不能删除");
            }
        }
        customer.setDeleted(true);
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
