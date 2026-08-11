package com.pulsetrack.backend.user;

import java.util.Optional;

import com.pulsetrack.backend.access.AccessProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promeut au demarrage le compte declare dans {@code pulsetrack.access.admin-email}.
 *
 * <p>Complement de la promotion faite a l'inscription : celle-ci couvre le
 * compte qui n'existe pas encore, celle-la le compte deja cree avant que la
 * variable ne soit posee. Sans les deux, il resterait un cas ou l'administrateur
 * declare n'obtient jamais ses droits.
 *
 * <p>L'operation est volontairement idempotente et silencieuse quand il n'y a
 * rien a faire : elle s'execute a chaque demarrage, y compris apres chaque
 * redeploiement.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final AccessProperties properties;

    public AdminBootstrap(UserRepository users, AccessProperties properties) {
        this.users = users;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.hasAdminEmail()) {
            log.info("Aucun compte administrateur configure : l'espace d'administration reste ferme.");
            return;
        }

        String email = AuthService.normalizeEmail(properties.adminEmail());
        Optional<User> account = users.findByEmail(email);
        if (account.isEmpty()) {
            // Pas un avertissement : c'est l'etat normal tant que Nicolas ne
            // s'est pas inscrit depuis l'application mobile. La promotion aura
            // lieu a l'inscription.
            log.info("Compte administrateur declare mais pas encore inscrit ; il le sera a sa creation.");
            return;
        }

        User user = account.get();
        if (user.isAdmin()) {
            return;
        }

        user.changeRole(Role.ADMIN);
        users.save(user);
        log.info("Compte {} promu administrateur au demarrage.", user.getId());
    }
}
