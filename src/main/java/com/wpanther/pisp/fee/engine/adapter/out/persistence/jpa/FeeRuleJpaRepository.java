package com.wpanther.pisp.fee.engine.adapter.out.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeeRuleJpaRepository extends JpaRepository<FeeRuleEntity, UUID> {

    @Query("SELECT f FROM FeeRuleEntity f WHERE f.paymentType = :paymentType " +
           "AND f.scheme = :scheme AND f.chargeBearer = :chargeBearer " +
           "AND f.currency = :currency AND f.active = true")
    List<FeeRuleEntity> findActive(@Param("paymentType") String paymentType,
                                   @Param("scheme") String scheme,
                                   @Param("chargeBearer") String chargeBearer,
                                   @Param("currency") String currency);

    @Query("SELECT f FROM FeeRuleEntity f WHERE " +
           "(:paymentType IS NULL OR f.paymentType = :paymentType) AND " +
           "(:scheme IS NULL OR f.scheme = :scheme) AND " +
           "(:chargeBearer IS NULL OR f.chargeBearer = :chargeBearer) AND " +
           "(:feeType IS NULL OR f.feeType = :feeType) AND " +
           "(:currency IS NULL OR f.currency = :currency) AND " +
           "(:accountIdentification IS NULL OR f.accountIdentification = :accountIdentification) AND " +
           "(:active IS NULL OR f.active = :active)")
    Page<FeeRuleEntity> findByFilters(@Param("paymentType") String paymentType,
                                      @Param("scheme") String scheme,
                                      @Param("chargeBearer") String chargeBearer,
                                      @Param("feeType") String feeType,
                                      @Param("currency") String currency,
                                      @Param("accountIdentification") String accountIdentification,
                                      @Param("active") Boolean active,
                                      Pageable pageable);
}
