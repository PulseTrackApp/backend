package com.pulsetrack.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Fige la forme JSON des reponses paginees.
 *
 * <p>Sans ce reglage, un controleur qui renvoie un {@code Page} fait serialiser
 * {@code PageImpl}, une classe interne de Spring Data. L'application le signale
 * d'ailleurs au demarrage : « Serializing PageImpl instances as-is is not
 * supported, meaning that there is no guarantee about the stability of the
 * resulting JSON structure ». Autrement dit, la forme recue par le client
 * mobile n'est pas un contrat, et une montee de version de Spring Data peut la
 * changer — l'application cesserait alors d'afficher les seances sans qu'aucun
 * test ne l'ait vu venir.
 *
 * <p>{@code VIA_DTO} impose {@code PagedModel}, dont la forme est stable et
 * documentee :
 *
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": { "size": 20, "number": 0, "totalElements": 42, "totalPages": 3 }
 * }
 * </pre>
 *
 * <p>Les metadonnees passent donc sous {@code page}, alors qu'elles etaient a
 * la racine. C'est une rupture de contrat assumee, faite maintenant justement
 * parce que l'application n'est encore distribuee a personne.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}
