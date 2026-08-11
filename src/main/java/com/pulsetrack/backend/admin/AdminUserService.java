package com.pulsetrack.backend.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.access.ModuleAccessService;
import com.pulsetrack.backend.access.ModuleUsageRow;
import com.pulsetrack.backend.access.UserModule;
import com.pulsetrack.backend.access.UserModuleRepository;
import com.pulsetrack.backend.admin.dto.AdminStatsResponse;
import com.pulsetrack.backend.admin.dto.AdminUserDetailResponse;
import com.pulsetrack.backend.admin.dto.AdminUserResponse;
import com.pulsetrack.backend.common.error.BusinessRuleException;
import com.pulsetrack.backend.common.error.ResourceNotFoundException;
import com.pulsetrack.backend.user.Role;
import com.pulsetrack.backend.user.User;
import com.pulsetrack.backend.user.UserRepository;
import com.pulsetrack.backend.workout.WorkoutSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations de l'espace d'administration.
 *
 * <p>Chaque ecriture est journalisee avec l'auteur et la cible : modifier les
 * droits de quelqu'un sans laisser de trace rendrait toute enquete ulterieure
 * impossible, et c'est precisement le genre de geste dont on veut pouvoir rendre
 * compte.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final Duration RECENT = Duration.ofDays(7);
    private static final Duration MONTHLY = Duration.ofDays(30);

    private final UserRepository users;
    private final UserModuleRepository userModules;
    private final ModuleAccessService moduleAccess;
    private final WorkoutSessionRepository workouts;

    public AdminUserService(UserRepository users,
                            UserModuleRepository userModules,
                            ModuleAccessService moduleAccess,
                            WorkoutSessionRepository workouts) {
        this.users = users;
        this.userModules = userModules;
        this.moduleAccess = moduleAccess;
        this.workouts = workouts;
    }

    /**
     * Liste paginee, filtree sur un fragment d'adresse quand il est fourni.
     *
     * <p>Les droits de toute la page sont lus en une seule requete. Les
     * demander compte par compte ferait vingt allers-retours pour vingt lignes.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> list(String query, Pageable pageable) {
        Page<User> page = (query == null || query.isBlank())
                ? users.findAll(pageable)
                : users.findByEmailContainingIgnoreCase(query.trim(), pageable);

        Map<UUID, List<AppModule>> modulesByUser = modulesOf(page.getContent().stream()
                .map(User::getId)
                .toList());

        return page.map(user -> toResponse(user, modulesByUser.getOrDefault(user.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(UUID userId) {
        User user = require(userId);
        return new AdminUserDetailResponse(
                toResponse(user, modulesOf(List.of(userId)).getOrDefault(userId, List.of())),
                workouts.countByUserId(userId),
                workouts.findLastStartedAt(userId));
    }

    @Transactional
    public AdminUserResponse replaceModules(UUID userId, Set<AppModule> modules, UUID actingAdminId) {
        User user = require(userId);
        Set<AppModule> granted = moduleAccess.replace(userId, modules);
        log.info("Administrateur {} a fixe les modules du compte {} a {}", actingAdminId, userId, granted);
        return toResponse(user, sorted(granted));
    }

    /**
     * @throws BusinessRuleException si l'administrateur tente de se retirer son
     *                               propre role : le dernier d'entre eux
     *                               pourrait se dechoir, et plus personne
     *                               n'administrerait quoi que ce soit
     */
    @Transactional
    public AdminUserResponse changeRole(UUID userId, Role role, UUID actingAdminId) {
        if (userId.equals(actingAdminId) && role != Role.ADMIN) {
            throw new BusinessRuleException("Un administrateur ne peut pas se retirer son propre role.");
        }

        User user = require(userId);
        user.changeRole(role);
        users.save(user);
        log.info("Administrateur {} a fixe le role du compte {} a {}", actingAdminId, userId, role);
        return toResponse(user, modulesOf(List.of(userId)).getOrDefault(userId, List.of()));
    }

    /**
     * Supprime le compte et, par cascade en base, tout ce qui s'y rattache.
     *
     * @throws BusinessRuleException si l'administrateur vise son propre compte.
     *                               La suppression est definitive et sans
     *                               confirmation cote serveur : un clic mal
     *                               place effacerait l'acces qui permet de
     *                               reparer le reste
     */
    @Transactional
    public void delete(UUID userId, UUID actingAdminId) {
        if (userId.equals(actingAdminId)) {
            throw new BusinessRuleException("Un administrateur ne peut pas supprimer son propre compte.");
        }

        User user = require(userId);
        users.delete(user);
        moduleAccess.evict(userId);
        log.info("Administrateur {} a supprime le compte {}", actingAdminId, userId);
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        Instant now = Instant.now();
        Map<AppModule, Long> usage = userModules.countUsersByModule().stream()
                .collect(Collectors.toMap(ModuleUsageRow::module, ModuleUsageRow::users));

        // Tous les modules figurent dans la reponse, y compris ceux accordes a
        // personne : un tableau de bord troue obligerait le client a deviner si
        // la valeur est nulle ou absente.
        List<AdminStatsResponse.ModuleUsage> distribution = Arrays.stream(AppModule.values())
                .map(module -> new AdminStatsResponse.ModuleUsage(module, usage.getOrDefault(module, 0L)))
                .toList();

        return new AdminStatsResponse(
                users.count(),
                users.countByRole(Role.ADMIN),
                workouts.countActiveUsersSince(now.minus(RECENT)),
                workouts.countActiveUsersSince(now.minus(MONTHLY)),
                workouts.countByStartedAtGreaterThanEqual(startOfMonth(now)),
                distribution);
    }

    /**
     * Premier jour du mois en cours. Calcul en UTC, exact pour ce produit : le
     * fuseau vise, Africa/Ouagadougou, est a GMT+0 toute l'annee et ignore
     * l'heure d'ete.
     */
    private static Instant startOfMonth(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    private Map<UUID, List<AppModule>> modulesOf(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<AppModule>> grouped = new HashMap<>();
        for (UserModule row : userModules.findByUserIdIn(userIds)) {
            grouped.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getModule());
        }
        grouped.replaceAll((ignored, modules) -> sorted(modules));
        return grouped;
    }

    /**
     * Ordre de declaration de l'enumeration : l'ecran d'administration doit
     * afficher ses cases dans le meme ordre d'un compte a l'autre, sinon
     * comparer deux fiches devient un exercice de patience.
     */
    private static List<AppModule> sorted(Collection<AppModule> modules) {
        return modules.stream().sorted(Comparator.comparingInt(AppModule::ordinal)).toList();
    }

    private User require(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable."));
    }

    private static AdminUserResponse toResponse(User user, List<AppModule> modules) {
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt(), modules);
    }
}
