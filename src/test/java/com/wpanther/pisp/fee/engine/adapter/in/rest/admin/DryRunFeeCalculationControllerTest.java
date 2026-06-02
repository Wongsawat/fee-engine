package com.wpanther.pisp.fee.engine.adapter.in.rest.admin;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.FeeRuleDtoMapper;
import com.wpanther.pisp.fee.engine.application.port.in.DryRunFeeCalculationUseCase;
import com.wpanther.pisp.fee.engine.domain.model.*;
import com.wpanther.pisp.fee.engine.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DryRunFeeCalculationController.class)
@Import({FeeRuleDtoMapper.class, SecurityConfig.class})
class DryRunFeeCalculationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean DryRunFeeCalculationUseCase dryRunUseCase;

    @Test
    void dryRunReturnsCharges() throws Exception {
        when(dryRunUseCase.dryRun(any())).thenReturn(List.of(
                new Charge(ChargeBearer.BorneByDebtor, "CHARGEType001",
                        new InstructedAmount(new BigDecimal("1.50"), "GBP"),
                        new AccountRef("SortCodeAccountNumber", "123"))));

        mockMvc.perform(post("/admin/fee-rules/dry-run")
                        .with(jwt().authorities(() -> "SCOPE_fee-rules:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "rule": {
                                "paymentType": "DOMESTIC",
                                "scheme": "FPS",
                                "chargeBearer": "BorneByDebtor",
                                "chargeType": "CHARGEType001",
                                "feeType": "FLAT",
                                "flatAmount": 1.50,
                                "currency": "GBP"
                              },
                              "instructedAmount": { "amount": "100.00", "currency": "GBP" },
                              "debtorAccount": { "schemeName": "SortCodeAccountNumber",
                                                  "identification": "123" }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[0].type").value("CHARGEType001"))
                .andExpect(jsonPath("$.charges[0].amount.amount").value("1.50"));
    }

    @Test
    void dryRunWithNoPaymentContextReturnsEmptyCharges() throws Exception {
        mockMvc.perform(post("/admin/fee-rules/dry-run")
                        .with(jwt().authorities(() -> "SCOPE_fee-rules:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "rule": {
                                "paymentType": "DOMESTIC",
                                "scheme": "FPS",
                                "chargeBearer": "BorneByDebtor",
                                "chargeType": "CHARGEType001",
                                "feeType": "FLAT",
                                "flatAmount": 1.50,
                                "currency": "GBP"
                              }
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges").isEmpty());
    }

    @Test
    void rejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(post("/admin/fee-rules/dry-run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "rule": {
                                "paymentType": "DOMESTIC",
                                "scheme": "FPS",
                                "chargeBearer": "BorneByDebtor",
                                "chargeType": "CHARGEType001",
                                "feeType": "FLAT",
                                "flatAmount": 1.50,
                                "currency": "GBP"
                              }
                            }
                            """))
                .andExpect(status().isUnauthorized());
    }
}
