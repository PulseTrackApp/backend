package com.pulsetrack.backend.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration de securite, validee au demarrage : une application mal
 * configuree doit refuser de demarrer plutot que de signer des jetons avec un
 * secret vide.
 *
 * @param jwt        parametres de signature et de duree de vie des jetons
 * @param cors       origines autorisees pour un client navigateur
 * @param encryption secret servant a chiffrer les donnees sensibles au repos
 */
@ConfigurationProperties(prefix = "pulsetrack.security")
@Validated
public record SecurityProperties(@NotNull @Valid Jwt jwt,
                                 @NotNull @Valid Cors cors,
                                 @NotNull @Valid Encryption encryption) {

    /**
     * @param secret         cle HMAC ; 32 caracteres minimum, impose par HS256
     * @param issuer         valeur du claim {@code iss}, verifiee a la lecture
     * @param accessTokenTtl duree de validite d'un jeton d'acces
     */
    public record Jwt(
            @NotBlank @Size(min = 32, message = "le secret JWT doit faire au moins 32 caracteres")
            String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {
    }

    /**
     * @param allowedOrigins liste blanche d'origines ; jamais {@code *}, car les
     *                       requetes portent un en-tete Authorization
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * Chiffrement au repos de la cle API Gemini de l'utilisateur.
     *
     * <p>Le secret ne vit jamais en base : c'est la configuration du serveur qui
     * detient de quoi dechiffrer. Une copie de la base, seule, ne rend donc
     * aucune cle exploitable.
     *
     * @param password secret maitre ; en production, uniquement par variable
     *                 d'environnement
     * @param salt     sel en hexadecimal, attendu par {@code Encryptors.delux}
     */
    public record Encryption(
            @NotBlank @Size(min = 16, message = "le secret de chiffrement doit faire au moins 16 caracteres")
            String password,
            @NotBlank
            @Pattern(regexp = "[0-9a-fA-F]+", message = "le sel doit etre une chaine hexadecimale")
            String salt) {
    }
}
