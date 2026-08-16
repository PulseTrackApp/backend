package com.pulsetrack.backend.admin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.pulsetrack.backend.access.AppModule;
import com.pulsetrack.backend.admin.dto.AdminModuleResponse;
import com.pulsetrack.backend.admin.dto.AdminStatsResponse;
import com.pulsetrack.backend.admin.dto.AdminUserDetailResponse;
import com.pulsetrack.backend.admin.dto.AdminUserResponse;
import com.pulsetrack.backend.admin.dto.UpdateModulesRequest;
import com.pulsetrack.backend.admin.dto.UpdateRoleRequest;
import com.pulsetrack.backend.admin.dto.UpdateSubscriptionRequest;
import com.pulsetrack.backend.billing.SubscriptionService;
import com.pulsetrack.backend.billing.dto.SubscriptionResponse;
import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.workout.WorkoutMetricsBackfill;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espace d'administration, consomme par l'application desktop.
 *
 * <p>Aucune annotation de securite ici : l'acces est ferme sur
 * {@code /api/v1/admin/**} dans la chaine de filtres. Un endpoint ajoute demain
 * dans cette classe nait donc protege, sans dependre de la vigilance de celui
 * qui l'ecrit.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminUserService adminUsers;
    private final WorkoutMetricsBackfill backfill;
    private final SubscriptionService subscriptions;

    public AdminController(AdminUserService adminUsers,
                           WorkoutMetricsBackfill backfill,
                           SubscriptionService subscriptions) {
        this.adminUsers = adminUsers;
        this.backfill = backfill;
        this.subscriptions = subscriptions;
    }

    /**
     * Catalogue des modules. Sert a construire l'ecran des droits sans coder la
     * liste en dur cote client : ajouter un module ne demandera alors aucune
     * nouvelle version de l'application desktop.
     */
    @GetMapping("/modules")
    public List<AdminModuleResponse> modules() {
        return Arrays.stream(AppModule.values())
                .map(module -> new AdminModuleResponse(module, module.label(), true))
                .toList();
    }

    @GetMapping("/users")
    public Page<AdminUserResponse> listUsers(@RequestParam(required = false) String q,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return adminUsers.list(q, pageable);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetailResponse user(@PathVariable UUID id) {
        return adminUsers.detail(id);
    }

    /**
     * {@code PUT} et remplacement complet, non {@code PATCH} : l'ecran envoie
     * l'etat de ses cases a cocher, et rejouer l'appel ne peut pas produire un
     * etat different de celui qui est affiche.
     */
    @PutMapping("/users/{id}/modules")
    public AdminUserResponse replaceModules(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody UpdateModulesRequest request) {
        return adminUsers.replaceModules(id, request.modules(), AuthenticatedUser.idOf(jwt));
    }

    @PutMapping("/users/{id}/role")
    public AdminUserResponse changeRole(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody UpdateRoleRequest request) {
        return adminUsers.changeRole(id, request.role(), AuthenticatedUser.idOf(jwt));
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        adminUsers.delete(id, AuthenticatedUser.idOf(jwt));
    }

    /**
     * Pose ou remplace le droit d'usage d'un compte.
     *
     * <p>Seul moyen d'accorder un acces tant qu'aucun encaissement automatique
     * n'existe : compte offert, abonnement encaisse hors ligne, compte suspendu.
     * C'est aussi ce qui permet de mettre un compte de test en {@code EXPIRED}
     * pour eprouver l'ecran de paiement sans attendre la fin d'un essai.
     */
    @PutMapping("/users/{id}/subscription")
    public SubscriptionResponse updateSubscription(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateSubscriptionRequest request) {
        return subscriptions.grant(id, request.status(), request.planCode(),
                request.periodEnd(), request.note());
    }

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminUsers.stats();
    }

    /**
     * Rejoue le calcul des metriques sur toutes les seances enregistrees.
     *
     * <p>A appeler apres une correction de formule : les metriques sont figees a
     * l'enregistrement, et sans ce geste l'historique continue d'afficher les
     * chiffres produits par l'ancien calcul. L'operation est idempotente, et la
     * reponse dit combien de seances ont reellement change.
     *
     * <p>Ne renvoie aucune donnee personnelle, seulement des compteurs : un
     * administrateur declenche une maintenance, il ne lit pas les parcours des
     * autres.
     */
    @PostMapping("/workouts/recompute-metrics")
    public WorkoutMetricsBackfill.Result recomputeWorkoutMetrics(@AuthenticationPrincipal Jwt jwt) {
        WorkoutMetricsBackfill.Result result = backfill.recomputeAll();
        log.info("Administrateur {} a relance le calcul des metriques : {}",
                AuthenticatedUser.idOf(jwt), result);
        return result;
    }
}
