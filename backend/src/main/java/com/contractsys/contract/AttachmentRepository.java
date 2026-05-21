package com.contractsys.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByContractIdOrderByUploadedAtDesc(Long contractId);
}
