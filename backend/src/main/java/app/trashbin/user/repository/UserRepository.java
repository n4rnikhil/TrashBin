package app.trashbin.user.repository;

import app.trashbin.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** {@code email} must already be normalized (trimmed, lower-case). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
