package bf.lycee.gestion.service;

/** Donnée ou fichier fourni invalide : renvoyé au client en HTTP 400. */
public class SaisieInvalideException extends RuntimeException {

    public SaisieInvalideException(String message) {
        super(message);
    }
}
