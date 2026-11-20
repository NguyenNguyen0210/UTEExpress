package com.uteexpress.service;

import com.uteexpress.dto.customer.AddressFormRequest;
import com.uteexpress.entity.CustomerAddress;
import com.uteexpress.entity.User;
import com.uteexpress.exception.ResourceNotFoundException;
import com.uteexpress.repository.CustomerAddressRepository;
import com.uteexpress.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAddressService {

    private final CustomerAddressRepository customerAddressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CustomerAddress> listAddresses(Long customerId) {
        return customerAddressRepository.findByCustomerIdOrderByIsDefaultDescIdDesc(customerId);
    }

    @Transactional
    public CustomerAddress addAddress(Long customerId, AddressFormRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản khách hàng"));

        List<CustomerAddress> existingAddresses =
                customerAddressRepository.findByCustomerIdOrderByIsDefaultDescIdDesc(customerId);

        boolean shouldBeDefault = request.isDefault() || existingAddresses.isEmpty();
        if (shouldBeDefault && !existingAddresses.isEmpty()) {
            unsetExistingDefaults(existingAddresses, null);
        }

        CustomerAddress address = CustomerAddress.builder()
                .customer(customer)
                .label(request.getLabel().trim())
                .contactName(request.getContactName().trim())
                .contactPhone(request.getContactPhone().trim())
                .province(request.getProvince().trim())
                .district(request.getDistrict().trim())
                .addressDetail(request.getAddressDetail().trim())
                .isDefault(shouldBeDefault)
                .build();

        return customerAddressRepository.save(address);
    }

    @Transactional
    public void setDefaultAddress(Long customerId, Long addressId) {
        CustomerAddress target = customerAddressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ trong sổ địa chỉ của bạn"));

        List<CustomerAddress> existingAddresses =
                customerAddressRepository.findByCustomerIdOrderByIsDefaultDescIdDesc(customerId);
        unsetExistingDefaults(existingAddresses, addressId);

        target.setIsDefault(true);
        customerAddressRepository.save(target);
    }

    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        CustomerAddress target = customerAddressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ trong sổ địa chỉ của bạn"));

        boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());
        customerAddressRepository.delete(target);

        if (wasDefault) {
            customerAddressRepository.findByCustomerIdOrderByIsDefaultDescIdDesc(customerId).stream()
                    .filter(addr -> !addr.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(nextDefault -> {
                        nextDefault.setIsDefault(true);
                        customerAddressRepository.save(nextDefault);
                    });
        }
    }

    private void unsetExistingDefaults(List<CustomerAddress> addresses, Long excludeAddressId) {
        for (CustomerAddress addr : addresses) {
            if (Boolean.TRUE.equals(addr.getIsDefault())
                    && (excludeAddressId == null || !addr.getId().equals(excludeAddressId))) {
                addr.setIsDefault(false);
                customerAddressRepository.save(addr);
            }
        }
    }
}
