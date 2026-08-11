package com.pulsetrack.backend.access;

import java.util.Arrays;
import java.util.Set;

import com.pulsetrack.backend.access.dto.ModuleStateResponse;
import com.pulsetrack.backend.access.dto.ModulesResponse;
import com.pulsetrack.backend.common.security.AuthenticatedUser;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ce que le compte courant a le droit d'utiliser.
 *
 * <p>Appele au demarrage de l'application et a chaque retour au premier plan.
 * La route n'est elle-meme protegee par aucun module : un client incapable de
 * savoir ce qui lui est ouvert ne pourrait plus rien afficher du tout.
 */
@RestController
@RequestMapping("/api/v1/me/modules")
public class ModuleController {

    private final ModuleAccessService moduleAccess;

    public ModuleController(ModuleAccessService moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    /**
     * Renvoie tous les modules connus avec leur etat, jamais les seuls modules
     * actifs : c'est ce qui permet au client de presenter une rubrique grisee
     * plutot que de la faire disparaitre, et de garder un menu stable.
     */
    @GetMapping
    public ModulesResponse myModules(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        Set<AppModule> enabled = moduleAccess.enabledFor(
                AuthenticatedUser.idOf(jwt), AdminAuthority.isAdmin(authentication));

        return new ModulesResponse(Arrays.stream(AppModule.values())
                .map(module -> new ModuleStateResponse(module, enabled.contains(module)))
                .toList());
    }
}
