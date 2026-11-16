package bf.lycee.gestion.service;

/** Ressource inexistante : renvoyée au client en HTTP 404. */
public class IntrouvableException extends RuntimeException {

    public IntrouvableException(String message) {
        super(message);
    }
}
