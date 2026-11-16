package bf.lycee.gestion.web;

import bf.lycee.gestion.service.IntrouvableException;
import bf.lycee.gestion.service.RegleMetierException;
import bf.lycee.gestion.service.SaisieInvalideException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduit les erreurs métier en réponses HTTP au format « problem+json ». */
@RestControllerAdvice
public class GestionErreurs extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IntrouvableException.class)
    public ProblemDetail introuvable(IntrouvableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SaisieInvalideException.class)
    public ProblemDetail saisieInvalide(SaisieInvalideException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RegleMetierException.class)
    public ProblemDetail regleMetier(RegleMetierException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrite(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "L'opération viole une contrainte d'intégrité des données");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail modificationConcurrente(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Cet enregistrement a été modifié par un autre utilisateur ; rechargez-le puis réessayez");
    }
}
