package bf.lycee.gestion.service;

/** Violation d'une règle de gestion : renvoyée au client en HTTP 409. */
public class RegleMetierException extends RuntimeException {

    public RegleMetierException(String message) {
        super(message);
    }
}
