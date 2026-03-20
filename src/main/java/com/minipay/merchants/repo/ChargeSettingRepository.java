package com.minipay.merchants.repo;

import com.minipay.merchants.domain.ChargeSetting;
import com.minipay.merchants.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ChargeSettingRepository extends JpaRepository<ChargeSetting, Long> {
    Optional<ChargeSetting> findByMerchant(Merchant merchant);
}
