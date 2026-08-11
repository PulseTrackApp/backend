package com.pulsetrack.backend.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** L'email passe doit deja etre normalise en minuscules. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Recherche de l'ecran d'administration. Insensible a la casse bien que les
     * adresses soient stockees en minuscules : c'est ce que l'administrateur
     * tape qui est imprevisible, pas ce qui est en base.
     */
    Page<User> findByEmailContainingIgnoreCase(String fragment, Pageable pageable);

    long countByRole(Role role);
}
