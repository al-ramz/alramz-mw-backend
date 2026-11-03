package com.alramz.service.impl;

import com.alramz.client.ETradeClient;
import com.alramz.config.ETradeTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DuplicateCheckServiceImplTest {

    @Mock
    private ETradeTokenProvider etradeTokenProvider;

    @Mock
    private ETradeClient etradeClient;

    @Mock
    private NinTradingNumberValidationService ninTradingNumberValidationService;

    @Mock
    private DuplicateCheckHelper duplicateCheckHelper;

    private DuplicateCheckServiceImpl duplicateCheckService;

    @BeforeEach
    void setUp() {
        duplicateCheckService = new DuplicateCheckServiceImpl(duplicateCheckHelper);
    }

    @Test
    void checkDuplicates_shouldReturnAllFalseWhenAllNull() {
        boolean[] result = duplicateCheckService.checkDuplicates(null, null, null, null);

        assertThat(result).hasSize(4).containsExactly(false, false, false, false);
    }

    @Test
    void checkDuplicates_shouldCheckNinWhenProvided() {
        when(duplicateCheckHelper.checkNinExists("NIN123")).thenReturn(true);

        boolean[] result = duplicateCheckService.checkDuplicates("NIN123", null, null, null);

        assertThat(result).containsExactly(true, false, false, false);
        verify(duplicateCheckHelper).checkNinExists("NIN123");
    }

    @Test
    void checkDuplicates_shouldCheckEidWhenProvided() {
        when(duplicateCheckHelper.checkEidExists("EID123")).thenReturn(true);

        boolean[] result = duplicateCheckService.checkDuplicates(null, "EID123", null, null);

        assertThat(result).containsExactly(false, true, false, false);
        verify(duplicateCheckHelper).checkEidExists("EID123");
    }

    @Test
    void checkDuplicates_shouldCheckEmailWhenProvided() {
        when(duplicateCheckHelper.checkEmailExists("test@example.com")).thenReturn(true);

        boolean[] result = duplicateCheckService.checkDuplicates(null, null, "test@example.com", null);

        assertThat(result).containsExactly(false, false, true, false);
        verify(duplicateCheckHelper).checkEmailExists("test@example.com");
    }

    @Test
    void checkDuplicates_shouldCheckPassportWhenProvided() {
        when(duplicateCheckHelper.checkPassportExists("P1234567")).thenReturn(true);

        boolean[] result = duplicateCheckService.checkDuplicates(null, null, null, "P1234567");

        assertThat(result).containsExactly(false, false, false, true);
        verify(duplicateCheckHelper).checkPassportExists("P1234567");
    }
}
