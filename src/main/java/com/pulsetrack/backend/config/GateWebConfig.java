package com.pulsetrack.backend.config;

import com.pulsetrack.backend.billing.SubscriptionInterceptor;
import com.pulsetrack.backend.billing.SubscriptionService;
import com.pulsetrack.backend.client.ClientProperties;
import com.pulsetrack.backend.client.ClientVersionInterceptor;
import com.pulsetrack.backend.user.AccountStatusService;
import com.pulsetrack.backend.user.DisabledAccountInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Branche les deux verrous transverses sur l'API.
 *
 * <p><strong>L'ordre compte.</strong> La version du client est verifiee avant le
 * droit d'usage : une application trop ancienne doit apprendre qu'elle est
 * perimee, et non qu'il faut payer. Lui montrer un ecran de paiement alors
 * qu'elle ne sait meme pas afficher celui-la enverrait l'utilisateur dans une
 * impasse.
 *
 * <p>Le controle des modules reste dans sa propre configuration, et s'execute
 * apres : refuser une rubrique fermee a un compte qui de toute facon doit payer
 * ou se mettre a jour serait la moins utile des trois reponses.
 */
@Configuration
public class GateWebConfig implements WebMvcConfigurer {

    private final ClientProperties clientProperties;
    private final SubscriptionService subscriptions;
    private final AccountStatusService accountStatuses;

    public GateWebConfig(ClientProperties clientProperties,
                         SubscriptionService subscriptions,
                         AccountStatusService accountStatuses) {
        this.clientProperties = clientProperties;
        this.subscriptions = subscriptions;
        this.accountStatuses = accountStatuses;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // En premier : un compte suspendu doit l'apprendre, et non s'entendre
        // dire de mettre a jour son application ou de payer — deux corrections
        // qui ne rouvriraient rien.
        registry.addInterceptor(new DisabledAccountInterceptor(accountStatuses))
                .addPathPatterns("/api/**")
                .order(5);
        registry.addInterceptor(new ClientVersionInterceptor(clientProperties))
                .addPathPatterns("/api/**")
                .order(10);
        registry.addInterceptor(new SubscriptionInterceptor(subscriptions))
                .addPathPatterns("/api/**")
                .order(20);
    }
}
