package com.pulsetrack.backend.access;

/**
 * Levee quand un compte appelle une route dont le module ne lui est pas accorde.
 *
 * <p>Porte le module en cause pour que la reponse d'erreur le nomme : sans lui,
 * l'application cliente saurait qu'on lui refuse quelque chose sans pouvoir dire
 * quoi, et n'aurait d'autre choix qu'un message d'echec generique.
 */
public class ModuleLockedException extends RuntimeException {

    private final transient AppModule module;

    public ModuleLockedException(AppModule module) {
        super("Le module " + module.name() + " n'est pas activé sur ce compte.");
        this.module = module;
    }

    public AppModule module() {
        return module;
    }
}
