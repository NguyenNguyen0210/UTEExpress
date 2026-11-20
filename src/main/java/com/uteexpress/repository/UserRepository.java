package com.uteexpress.repository;

import com.uteexpress.entity.User;
import com.uteexpress.enums.Role;
import com.uteexpress.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    List<User> findByHubIdAndRoleAndStatusOrderByFullNameAsc(Long hubId, Role role, UserStatus status);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findByHubIdAndRoleOrderByFullNameAsc(Long hubId, Role role);
}
