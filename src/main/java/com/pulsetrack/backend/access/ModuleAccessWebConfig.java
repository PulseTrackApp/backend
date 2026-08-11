package com.pulsetrack.backend.access;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Branche le controle des modules sur toutes les routes de l'API.
 *
 * <p>Enregistre sur {@code /api/**} plutot que sur la liste des prefixes
 * proteges : c'est {@link AppModule} qui decide si une route releve d'un module,
 * et lui seul. Dupliquer la liste ici la laisserait diverger le jour ou un
 * module s'ajoute.
 *
 * <p>Configuration distincte de {@code WebConfig}, qui ne s'occupe que de la
 * serialisation des pages : deux sujets sans rapport, dont le melange
 * obligerait a relire l'un pour comprendre l'autre.
 */
@Configuration
public class ModuleAccessWebConfig implements WebMvcConfigurer {

    private final ModuleAccessService moduleAccess;

    public ModuleAccessWebConfig(ModuleAccessService moduleAccess) {
        this.moduleAccess = moduleAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ModuleAccessInterceptor(moduleAccess))
                .addPathPatterns("/api/**");
    }
}
