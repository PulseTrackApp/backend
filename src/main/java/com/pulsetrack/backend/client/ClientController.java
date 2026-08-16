package com.pulsetrack.backend.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ce que l'API exige de l'application appelante.
 *
 * <p>Route <strong>ouverte, non authentifiee et jamais verrouillee</strong> :
 * c'est celle qu'un client interroge pour savoir s'il doit se mettre a jour. La
 * fermer rendrait le dispositif inutilisable, puisqu'une application perimee ne
 * pourrait meme pas apprendre qu'elle l'est — ni un utilisateur deconnecte
 * comprendre pourquoi sa connexion echoue.
 *
 * <p>Elle n'expose rien de sensible : un numero de version et une adresse de
 * magasin, deux informations publiques par nature.
 */
@RestController
@RequestMapping("/api/v1/client")
public class ClientController {

    private final ClientProperties properties;

    public ClientController(ClientProperties properties) {
        this.properties = properties;
    }

    /**
     * Exigences courantes.
     *
     * <p>A appeler au demarrage. Tant que {@code enforced} vaut {@code false},
     * une version inferieure au minimum n'est pas refusee : c'est le moment
     * d'inviter a la mise a jour sans bloquer. Une fois a {@code true}, toute
     * requete d'une version anterieure est refusee en {@code 426}.
     */
    @GetMapping("/requirements")
    public ClientRequirementsResponse requirements() {
        return new ClientRequirementsResponse(
                properties.minimum().toString(),
                properties.enforced(),
                blankToNull(properties.androidStoreUrl()),
                blankToNull(properties.iosStoreUrl()),
                ClientProperties.VERSION_HEADER,
                ClientProperties.PLATFORM_HEADER);
    }

    /**
     * @param minimumVersion version minimale acceptee
     * @param enforced       vrai si le refus est deja actif ; faux tant que le
     *                       dispositif est en observation
     * @param versionHeader  nom de l'en-tete a envoyer, donne ici pour que le
     *                       client n'ait pas a le coder en dur d'apres une
     *                       documentation qui pourrait vieillir
     */
    public record ClientRequirementsResponse(
            String minimumVersion,
            boolean enforced,
            String androidStoreUrl,
            String iosStoreUrl,
            String versionHeader,
            String platformHeader) {
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
