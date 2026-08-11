package com.pulsetrack.backend.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pulsetrack.backend.common.ratelimit.FixedWindowRateLimiter;
import com.pulsetrack.backend.user.Role;
import com.pulsetrack.backend.user.TokenService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Securite de l'API : stateless, fermee par defaut, jetons JWT signes en HS256.
 *
 * <p>La validation des jetons est deleguee au support OAuth2 Resource Server
 * plutot qu'a un filtre maison : signature, expiration et emetteur sont alors
 * verifies par du code eprouve.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Chemins ouverts enumeres un par un, et non
                        // `/api/v1/auth/**` : un joker rend public tout ce qu'on
                        // ajoutera plus tard sous ce prefixe, y compris ce qui ne
                        // devait pas l'etre. C'est exactement le cas de
                        // `/api/v1/auth/logout`, qui exige un jeton d'acces et que
                        // le joker aurait ouvert a tout le monde.
                        //
                        // La methode est contrainte elle aussi : un GET sur
                        // `/api/v1/auth/login` n'a aucune raison d'exister, et le
                        // laisser passer offre une surface de plus.
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                // Ouvert par nature : on vient le solliciter
                                // precisement parce que le jeton d'acces a expire.
                                // C'est la possession du jeton de renouvellement,
                                // verifiee en base, qui authentifie l'appel.
                                "/api/v1/auth/refresh",
                                // Ouverts par necessite : quelqu'un qui a oublie
                                // son mot de passe ne peut par definition pas
                                // s'authentifier. C'est la possession de la boite
                                // aux lettres qui fait foi, et la limitation de
                                // debit qui empeche d'en abuser.
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // L'espace d'administration est ferme au niveau de la
                        // chaine, avant tout controleur. Le poser ici plutot que
                        // par annotation sur chaque methode garantit qu'un
                        // endpoint ajoute demain sous /admin nait protege, sans
                        // dependre de la vigilance de celui qui l'ecrit.
                        .requestMatchers("/api/v1/admin/**").hasRole(Role.ADMIN.name())
                        // Documentation de l'API. Sans danger : le profil `prod`
                        // desactive springdoc, ces routes n'y existent donc pas.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Le navigateur envoie un preflight sans en-tete Authorization.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                // API sans cookie de session : le CSRF n'a pas de prise, et le
                // desactiver evite de rejeter les POST des clients mobiles.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())));
        return http.build();
    }

    /**
     * Transforme la revendication {@code role} du jeton en autorite Spring
     * Security.
     *
     * <p>Sans ce convertisseur, le support Resource Server ne lit que la
     * revendication {@code scope} et prefixe les autorites par {@code SCOPE_} :
     * {@code hasRole("ADMIN")} ne trouverait jamais rien et l'espace
     * d'administration resterait ferme a tout le monde, y compris a un
     * administrateur legitime.
     *
     * <p>Un jeton emis avant l'arrivee des roles ne porte pas la revendication.
     * Il reste valable pour les routes ordinaires — sa signature est intacte —
     * mais n'ouvre aucune porte d'administration, ce qui est le comportement
     * souhaite : le privilege se demande, il ne se suppose pas.
     */
    private JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Encodeur delegant : hache en bcrypt et prefixe l'algorithme dans le hash
     * ({@code {bcrypt}...}), ce qui permettra d'en changer sans invalider les
     * mots de passe existants.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Chiffre la cle API Gemini avant stockage (AES-256-GCM, cle derivee par
     * PBKDF2 du secret de configuration).
     *
     * <p>Contrairement a un mot de passe, une cle API doit pouvoir etre relue
     * pour appeler le service : on la chiffre, on ne la hache pas. Le secret
     * vivant hors de la base, une fuite de la base seule ne suffit pas.
     */
    @Bean
    TextEncryptor apiKeyEncryptor(SecurityProperties properties) {
        return Encryptors.delux(properties.encryption().password(), properties.encryption().salt());
    }

    /**
     * Compteur partage par les limites appliquees aux endpoints ouverts.
     *
     * <p>Un seul bean : les compteurs vivent dans son instance, en avoir deux
     * reviendrait a doubler silencieusement chaque plafond.
     */
    @Bean
    FixedWindowRateLimiter authRateLimiterCounters() {
        return new FixedWindowRateLimiter(Clock.systemUTC());
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(hmacKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Verifie l'emetteur en plus de l'expiration : un jeton signe par une
        // autre application partageant le secret reste refuse.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    /**
     * Autorisations CORS, uniquement si des origines sont declarees.
     *
     * <p>Le CORS ne concerne que les clients navigateur. Une application mobile
     * native n'envoie pas d'en-tete {@code Origin} et n'est jamais soumise a ce
     * controle : sur un projet sans front web, la liste reste vide et aucune
     * regle n'est enregistree. Exiger une valeur reviendrait a faire inventer
     * un domaine a l'exploitant pour satisfaire la configuration.
     *
     * <p>Liste vide veut bien dire « aucune page web autorisee », et non
     * « toutes » : la source ne connait alors aucun chemin, le filtre CORS
     * n'ajoute aucun en-tete, et le navigateur bloque de lui-meme la reponse.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowedOrigins = properties.cors().allowedOrigins();
        if (allowedOrigins.isEmpty()) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Pas de credentials : l'authentification passe par l'en-tete
        // Authorization, pas par un cookie.
        configuration.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private SecretKey hmacKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
