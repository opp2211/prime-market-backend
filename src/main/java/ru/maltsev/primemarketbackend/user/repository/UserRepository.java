package ru.maltsev.primemarketbackend.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findWithRolesByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = { "roles", "roles.permissions" })
    Optional<User> findWithRolesById(Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
