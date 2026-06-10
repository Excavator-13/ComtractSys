package com.contractsys.contract;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ContractNumberService {
    public String temporaryNumber() {
        return "TMP" + Long.toUnsignedString(System.nanoTime(), 36);
    }

    public String contractNo(Long id) {
        return "HT" + datePart() + String.format("%010d", id);
    }

    public String customerNo(Long id) {
        return "KH" + datePart() + String.format("%010d", id);
    }

    private String datePart() {
        return LocalDate.now().toString().replace("-", "");
    }
}
