package com.wpanther.pisp.fee.engine.adapter.in.rest.admin;

import com.wpanther.pisp.fee.engine.adapter.in.rest.admin.dto.FeeRuleDtoMapper;
import com.wpanther.pisp.fee.engine.application.port.in.ManageFeeRulesUseCase;
import com.wpanther.pisp.fee.engine.application.port.out.FeeRuleDetails;
import com.wpanther.pisp.fee.engine.domain.exception.FeeRuleNotFoundException;
import com.wpanther.pisp.fee.engine.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeeRuleAdminController.class)
@Import({FeeRuleDtoMapper.class, SecurityConfig.class})
class FeeRuleAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ManageFeeRulesUseCase manageFeeRulesUseCase;

    private static final UUID RULE_ID = UUID.randomUUID();

    private FeeRuleDetails testDetails() {
        return new FeeRuleDetails(RULE_ID, "DOMESTIC", "FPS", "BorneByDebtor", null,
                "CHARGEType001", "FLAT", new BigDecimal("1.50"), null, null, "GBP",
                true, 0, Instant.now(), "system", Instant.now(), "system");
    }

    @Test
    void listRules() throws Exception {
        var page = new org.springframework.data.domain.PageImpl<>(
                List.of(testDetails()),
                org.springframework.data.domain.PageRequest.of(0, 20),
                1L);
        when(manageFeeRulesUseCase.findAll(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/admin/fee-rules")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].feeType").value("FLAT"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void getRuleById() throws Exception {
        when(manageFeeRulesUseCase.findById(RULE_ID)).thenReturn(testDetails());

        mockMvc.perform(get("/admin/fee-rules/{id}", RULE_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.feeType").value("FLAT"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void getRuleByIdReturns404WhenNotFound() throws Exception {
        when(manageFeeRulesUseCase.findById(RULE_ID))
                .thenThrow(new FeeRuleNotFoundException(RULE_ID));

        mockMvc.perform(get("/admin/fee-rules/{id}", RULE_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRule() throws Exception {
        when(manageFeeRulesUseCase.create(any())).thenReturn(testDetails());

        mockMvc.perform(post("/admin/fee-rules")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "chargeType": "CHARGEType001",
                              "feeType": "FLAT",
                              "flatAmount": 1.50,
                              "currency": "GBP"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/admin/fee-rules/" + RULE_ID))
                .andExpect(jsonPath("$.feeType").value("FLAT"));
    }

    @Test
    void updateRule() throws Exception {
        when(manageFeeRulesUseCase.update(any())).thenReturn(testDetails());

        mockMvc.perform(put("/admin/fee-rules/{id}", RULE_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "chargeType": "CHARGEType001",
                              "feeType": "FLAT",
                              "flatAmount": 2.00,
                              "currency": "GBP",
                              "version": 0
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeType").value("FLAT"));
    }

    @Test
    void toggleStatus() throws Exception {
        when(manageFeeRulesUseCase.toggleStatus(RULE_ID, false, 0L)).thenReturn(testDetails());

        mockMvc.perform(patch("/admin/fee-rules/{id}/status", RULE_ID)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", "test-client"))
                                .authorities(() -> "SCOPE_fee-rules:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "active": false, "version": 0 }
                            """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/admin/fee-rules")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWriteWithoutWriteScope() throws Exception {
        mockMvc.perform(post("/admin/fee-rules")
                        .with(jwt().authorities(() -> "SCOPE_fee-rules:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "paymentType": "DOMESTIC",
                              "scheme": "FPS",
                              "chargeBearer": "BorneByDebtor",
                              "chargeType": "CHARGEType001",
                              "feeType": "FLAT",
                              "flatAmount": 1.50,
                              "currency": "GBP"
                            }
                            """))
                .andExpect(status().isForbidden());
    }
}
