package bf.lycee.gestion.security;

/** Expressions d'autorisation réutilisables dans {@code @PreAuthorize}. */
public final class Roles {

    public static final String DIRECTION = "hasAnyRole('DIRECTEUR', 'CENSEUR')";
    /** Gestion des élèves et des inscriptions. */
    public static final String SCOLARITE = "hasAnyRole('DIRECTEUR', 'CENSEUR', 'SURVEILLANT')";
    public static final String ENSEIGNANT = "hasRole('ENSEIGNANT')";
    public static final String SURVEILLANT = "hasRole('SURVEILLANT')";
    public static final String INTENDANT = "hasRole('INTENDANT')";
    public static final String CAISSE = "hasAnyRole('INTENDANT', 'ECONOME')";
    public static final String FINANCES_LECTURE = "hasAnyRole('DIRECTEUR', 'CENSEUR', 'INTENDANT', 'ECONOME')";

    private Roles() {
    }
}
