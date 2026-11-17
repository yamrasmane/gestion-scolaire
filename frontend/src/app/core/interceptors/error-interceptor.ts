import { HttpErrorResponse, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Les erreurs HTTP sont affichées par la page qui a lancé l'appel (message en
 * clair à côté du formulaire concerné). L'intercepteur se contente de les
 * tracer : il ne change jamais de page, car une ressource manquante
 * (traductions, image) renverrait alors l'utilisateur sur l'écran « Page
 * introuvable » au lieu de l'écran demandé.
 */
export function errorInterceptor(requete: HttpRequest<unknown>, suivant: HttpHandlerFn) {
  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      console.error(`Échec de l'appel ${requete.method} ${requete.url}`, erreur.status);
      return throwError(() => erreur);
    }),
  );
}
