package com.pulsetrack.backend.access;

import java.util.Optional;
import java.util.UUID;

import com.pulsetrack.backend.common.security.AuthenticatedUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuse les routes dont le module n'est pas accorde au compte appelant.
 *
 * <p>Intercepteur Spring MVC et non regle de la chaine de securite : place ici,
 * le refus traverse le {@code @RestControllerAdvice} de l'API et sort au format
 * RFC 9457 comme toutes les autres erreurs, en nommant le module bloque. Une
 * regle posee dans la chaine de securite produirait un corps different de celui
 * que le reste de l'API documente, et le client aurait deux formats d'erreur a
 * gerer.
 *
 * <p>L'authentification est deja faite quand cet intercepteur s'execute : la
 * chaine de filtres de securite precede entierement le {@code DispatcherServlet}.
 */
public class ModuleAccessInterceptor implements HandlerInterceptor {

    private final ModuleAccessService moduleAccess;

    public ModuleAccessInterceptor(ModuleAccessService moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<AppModule> module = AppModule.forPath(request.getRequestURI());
        if (module.isEmpty()) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            // Requete non authentifiee : ce n'est pas a cet intercepteur de la
            // refuser. La chaine de securite l'a deja fait, ou l'a laissee
            // passer en connaissance de cause — repondre « module ferme » ici
            // masquerait la vraie raison du refus.
            return true;
        }

        UUID userId = AuthenticatedUser.idOf(jwt);
        if (!moduleAccess.isEnabled(userId, AdminAuthority.isAdmin(authentication), module.get())) {
            throw new ModuleLockedException(module.get());
        }
        return true;
    }
}
