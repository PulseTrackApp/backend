package com.pulsetrack.backend.profile.dto;

import java.time.LocalDate;
import java.util.Set;

import com.pulsetrack.backend.common.domain.SportType;
import com.pulsetrack.backend.profile.FitnessLevel;
import com.pulsetrack.backend.profile.PrimaryGoal;
import com.pulsetrack.backend.profile.Sex;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Modification partielle du profil : <strong>seuls les champs presents changent</strong>.
 *
 * <p><strong>Pourquoi cet objet existe.</strong> {@code PUT /me/profile} remplace
 * le profil entier, ce qui est correct pour l'ecran d'accueil qui saisit tout
 * d'un coup. Mais un ecran qui ne corrige que le poids et rejoue un {@code PUT}
 * incomplet efface au passage la date de naissance et le sexe — les deux seuls
 * champs facultatifs — sans que rien ne l'en avertisse. Les champs obligatoires,
 * eux, sont proteges par la validation : un {@code PUT} amoute est refuse en
 * {@code 400}. Ce sont donc precisement les champs optionnels qui se perdaient
 * silencieusement.
 *
 * <p>Tous les champs sont ici des types enveloppes et tous sont nullables :
 * {@code null} veut dire « ne touche pas », jamais « efface ». <strong>Cette
 * route ne permet donc pas d'effacer une date de naissance</strong> ; c'est un
 * compromis assume, l'effacement passe par un {@code PUT} complet, geste
 * explicite.
 */
public record ProfilePatchRequest(
        @Size(max = 80) String displayName,
        @Min(80) @Max(260) Integer heightCm,
        @DecimalMin("20.0") @DecimalMax("400.0") Double currentWeightKg,
        @Past LocalDate birthDate,
        Sex sex,
        PrimaryGoal primaryGoal,
        FitnessLevel fitnessLevel,
        // @Size et non @NotEmpty : cette derniere refuse aussi la valeur nulle,
        // ce qui rendrait le champ obligatoire et viderait la route de son sens.
        // @Size ignore le nul et refuse la liste vide, exactement ce qu'il faut.
        @Size(min = 1, message = "au moins un sport pratique") Set<SportType> preferredSports) {

    /**
     * Un corps entierement vide ne changerait rien tout en repondant 200, ce qui
     * ferait croire a une modification enregistree.
     */
    public boolean isEmpty() {
        return displayName == null
                && heightCm == null
                && currentWeightKg == null
                && birthDate == null
                && sex == null
                && primaryGoal == null
                && fitnessLevel == null
                && preferredSports == null;
    }
}
