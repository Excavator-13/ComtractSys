package com.contractsys.customer;

import com.contractsys.common.ApiException;
import com.contractsys.common.PageRequests;
import com.contractsys.customer.dto.CustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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
        customer.setCustomerNo("KH" + LocalDate.now().toString().replace("-", "") + System.currentTimeMillis() % 100000);
        fill(customer, request);
        return customerRepository.save(customer);
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
