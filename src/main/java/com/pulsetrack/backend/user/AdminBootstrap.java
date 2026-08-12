package com.pulsetrack.backend.user;

import java.time.Instant;
import java.util.Optional;

import com.pulsetrack.backend.access.AccessProperties;
import com.pulsetrack.backend.access.ModuleAccessService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Met en place le compte administrateur declare dans
 * {@code pulsetrack.access.admin-email}.
 *
 * <p>Trois situations, selon ce que la configuration fournit et ce que la base
 * contient deja :
 *
 * <ul>
 *   <li>le compte existe et n'est pas administrateur : il est promu ;</li>
 *   <li>le compte n'existe pas et un mot de passe d'amorçage est fourni : il est
 *       cree, deja administrateur ;</li>
 *   <li>le compte n'existe pas et aucun mot de passe n'est fourni : rien, la
 *       promotion aura lieu a l'inscription.</li>
 * </ul>
 *
 * <p>Un compte deja present n'est <strong>jamais</strong> reecrit. Reappliquer
 * le mot de passe de l'environnement a chaque demarrage effacerait celui que
 * l'administrateur aurait change depuis l'application, sans que rien ne le
 * signale — et le redeploiement suivant recommencerait.
 *
 * <p>L'ensemble est idempotent : il s'execute a chaque demarrage, donc apres
 * chaque redeploiement.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final AccessProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final ModuleAccessService moduleAccess;

    public AdminBootstrap(UserRepository users,
                          AccessProperties properties,
                          PasswordEncoder passwordEncoder,
                          ModuleAccessService moduleAccess) {
        this.users = users;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.moduleAccess = moduleAccess;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.hasAdminEmail()) {
            log.info("Aucun compte administrateur configure : l'espace d'administration reste ferme.");
            return;
        }

        String email = AuthService.normalizeEmail(properties.adminEmail());
        Optional<User> existing = users.findByEmail(email);

        if (existing.isPresent()) {
            promote(existing.get());
            return;
        }
        create(email);
    }

    private void promote(User user) {
        if (user.isAdmin()) {
            return;
        }
        user.changeRole(Role.ADMIN);
        users.save(user);
        log.info("Compte {} promu administrateur au demarrage.", user.getId());
    }

    private void create(String email) {
        if (!properties.hasAdminPassword()) {
            // Etat normal tant que le compte n'a pas ete cree depuis
            // l'application : la promotion aura lieu a l'inscription.
            log.info("Compte administrateur declare mais pas encore inscrit ; il le sera a sa creation.");
            return;
        }

        if (!properties.adminPasswordIsLongEnough()) {
            // Journalise et abandonne, plutot que d'empecher le demarrage. Une
            // erreur de saisie sur une variable accessoire ne doit pas priver
            // d'API tous les utilisateurs de l'application mobile — la lecon de
            // PULSETRACK_FCM_ENABLED, qui avait fait tomber l'application
            // entiere pour une fonction secondaire.
            log.error("Mot de passe d'amorçage trop court ({} caracteres, minimum {}) :"
                            + " le compte administrateur n'a pas ete cree.",
                    properties.adminPassword().length(), AccessProperties.MIN_PASSWORD_LENGTH);
            return;
        }

        User admin = new User(email, passwordEncoder.encode(properties.adminPassword()), Instant.now());
        admin.changeRole(Role.ADMIN);
        // Adresse posee par l'exploitant dans la configuration du serveur, pas
        // saisie dans un formulaire : elle n'a rien a prouver. Sans cela, rendre
        // la verification obligatoire fermerait l'espace d'administration a
        // celui-la meme qui l'a configure, et personne ne pourrait le rouvrir.
        admin.markEmailVerified();
        User saved = users.save(admin);
        moduleAccess.grantDefaults(saved.getId());

        log.info("Compte administrateur {} cree au demarrage a partir du mot de passe d'amorçage."
                + " Changez ce mot de passe puis retirez la variable de l'environnement :"
                + " elle reste lisible en clair dans la configuration de la plateforme.", saved.getId());
    }
}
