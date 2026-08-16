package com.pulsetrack.backend.billing;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tarification et regle d'acces payant.
 *
 * <p><strong>Rien n'est encore facture.</strong> {@code enforced} vaut faux :
 * l'API repond comme avant, et le catalogue existe uniquement pour que
 * l'application puisse construire son ecran de tarifs des maintenant, marque
 * « a venir ». Le jour ou Nicolas decide, il n'y a qu'un booleen a basculer — pas
 * de livraison a preparer dans l'urgence.
 *
 * <p><strong>Les prix ci-dessous sont des valeurs d'attente.</strong> Ils vivent
 * en configuration et non en dur precisement pour qu'ils se corrigent sans
 * recompiler. Ils doivent etre relus avant toute publication.
 *
 * @param enforced  refuse les routes payantes aux comptes sans droit. Faux par
 *                  defaut, et a ne basculer qu'apres avoir verrouille les
 *                  anciennes applications ({@code pulsetrack.client.enforced}) :
 *                  sans cela, il suffit de ne pas mettre a jour pour continuer
 *                  gratuitement
 * @param trialDays duree de l'essai, comptee depuis la creation du compte. Elle
 *                  s'applique retroactivement aux comptes existants, ce qui est
 *                  voulu : ils ont deja consomme leur essai
 * @param plans     catalogue affiche par l'ecran de tarifs
 */
@ConfigurationProperties(prefix = "pulsetrack.billing")
public record BillingProperties(boolean enforced, int trialDays, List<Plan> plans) {

    /**
     * Une offre du catalogue.
     *
     * @param code         identifiant stable, utilise par l'API et par
     *                     l'administration. Le renommer casserait les
     *                     abonnements deja enregistres
     * @param priceAmount  montant, dans l'unite courante de la devise. En franc
     *                     CFA il n'y a pas de centime, d'ou un entier
     * @param availability {@code COMING_SOON} tant que le paiement n'existe pas
     * @param highlighted  offre mise en avant a l'ecran ; une seule devrait
     *                     l'etre
     */
    public record Plan(String code,
                       String name,
                       String description,
                       long priceAmount,
                       String currency,
                       BillingPeriod period,
                       Availability availability,
                       boolean highlighted,
                       List<String> features) {
    }

    /** Rythme de facturation. */
    public enum BillingPeriod {
        MONTHLY,
        YEARLY,
        LIFETIME
    }

    /** Etat d'une offre au catalogue. */
    public enum Availability {

        /** Annoncee, pas encore souscriptible. C'est l'etat de tout le catalogue aujourd'hui. */
        COMING_SOON,

        AVAILABLE,

        /** Retiree de la vente ; les abonnes en cours la conservent. */
        RETIRED
    }
}
