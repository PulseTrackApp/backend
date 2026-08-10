package com.pulsetrack.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** L'email passe doit deja etre normalise en minuscules. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
