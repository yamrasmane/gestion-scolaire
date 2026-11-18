package bf.lycee.gestion.rapports;

/** Échec d'édition d'un document : fichier Jasper absent, illisible ou invalide. */
public class RapportException extends RuntimeException {

    public RapportException(String message) {
        super(message);
    }

    public RapportException(String message, Throwable cause) {
        super(message, cause);
    }
}
