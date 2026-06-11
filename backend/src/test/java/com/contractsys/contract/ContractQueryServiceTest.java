package com.contractsys.contract;

import com.contractsys.user.SysUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractQueryServiceTest {
    @Test
    void exportAdvancedQueryUsesTenThousandRowsWithoutPageRequestClamp() {
        ContractRepository contractRepository = mock(ContractRepository.class);
        ContractQueryService service = new ContractQueryService(
                contractRepository, null, null, null, null, mock(ContractAccessGuard.class));
        when(contractRepository.advancedSearch(
                anyString(), nullable(ContractStatus.class), nullable(Long.class), nullable(Long.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        service.exportAdvancedQuery("", "", null, null, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contractRepository).advancedSearch(
                anyString(), nullable(ContractStatus.class), nullable(Long.class), nullable(Long.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class),
                pageable.capture()
        );
        assertEquals(10000, pageable.getValue().getPageSize());
    }

    @Test
    void exportAdvancedListUsesTenThousandRowsWithoutPageRequestClamp() {
        ContractRepository contractRepository = mock(ContractRepository.class);
        ContractAccessGuard accessGuard = mock(ContractAccessGuard.class);
        ContractQueryService service = new ContractQueryService(
                contractRepository, null, null, null, null, accessGuard);
        SysUser user = new SysUser();
        user.setId(7L);
        when(accessGuard.hasPermission(user, "contract:assign")).thenReturn(false);
        when(contractRepository.advancedSearchRelated(
                anyString(), nullable(ContractStatus.class), nullable(Long.class), nullable(Long.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class),
                eq(7L), anyBoolean(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        service.exportAdvancedList("", "", null, null, null, null, null, null, user);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contractRepository).advancedSearchRelated(
                anyString(), nullable(ContractStatus.class), nullable(Long.class), nullable(Long.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class),
                eq(7L), anyBoolean(), pageable.capture()
        );
        assertEquals(10000, pageable.getValue().getPageSize());
    }
}
