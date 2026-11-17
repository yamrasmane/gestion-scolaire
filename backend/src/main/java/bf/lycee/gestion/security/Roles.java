package bf.lycee.gestion.security;

/** Expressions d'autorisation réutilisables dans {@code @PreAuthorize}. */
public final class Roles {

    /**
     * Rôle d'administration : ouvre toutes les portes de l'application. Il
     * figure dans chaque expression ci-dessous, et dans les contrôles écrits
     * à la main (saisie des notes, validation des remises).
     */
    public static final String ADMIN_ROLE = "ADMIN";

    public static final String ADMIN = "hasRole('" + ADMIN_ROLE + "')";
    public static final String DIRECTION = "hasAnyRole('ADMIN', 'DIRECTEUR', 'CENSEUR')";
    /** Gestion des élèves et des inscriptions. */
    public static final String SCOLARITE = "hasAnyRole('ADMIN', 'DIRECTEUR', 'CENSEUR', 'SURVEILLANT')";
    public static final String ENSEIGNANT = "hasAnyRole('ADMIN', 'ENSEIGNANT')";
    public static final String SURVEILLANT = "hasAnyRole('ADMIN', 'SURVEILLANT')";
    public static final String INTENDANT = "hasAnyRole('ADMIN', 'INTENDANT')";
    public static final String CAISSE = "hasAnyRole('ADMIN', 'INTENDANT', 'ECONOME')";
    public static final String FINANCES_LECTURE =
            "hasAnyRole('ADMIN', 'DIRECTEUR', 'CENSEUR', 'INTENDANT', 'ECONOME')";

    private Roles() {
    }
}
