package com.uteexpress;

import com.uteexpress.entity.User;
import com.uteexpress.repository.HubRepository;
import com.uteexpress.repository.ServiceTypeRepository;
import com.uteexpress.repository.ShipmentRepository;
import com.uteexpress.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UteExpressApplicationTests {

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextLoadsAndFlywaySeedDataIsValid() {
        assertThat(hubRepository.count()).isGreaterThanOrEqualTo(6);
        assertThat(serviceTypeRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(6);
        assertThat(shipmentRepository.count()).isGreaterThanOrEqualTo(5);

        User shop = userRepository.findByEmailIgnoreCase("shop@uteexpress.vn").orElseThrow();
        assertThat(passwordEncoder.matches("123456", shop.getPasswordHash())).isTrue();
    }
}
