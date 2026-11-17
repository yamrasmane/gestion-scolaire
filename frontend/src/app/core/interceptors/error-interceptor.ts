import { HttpErrorResponse, HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { config } from '@core/config';

/**
 * Les erreurs de l'API sont affichées dans la page qui a lancé l'appel
 * (message en clair à côté du formulaire concerné) : l'intercepteur les laisse
 * passer. Les autres erreurs mènent aux pages 403 / 404 / 500 du gabarit.
 */
export function errorInterceptor(requete: HttpRequest<unknown>, suivant: HttpHandlerFn) {
  const router = inject(Router);
  const pagesErreur = [403, 404, 500];

  return suivant(requete).pipe(
    catchError((erreur: HttpErrorResponse) => {
      const versApi = requete.url.startsWith(config.apiUrl);
      if (!versApi && pagesErreur.includes(erreur.status)) {
        router.navigateByUrl(`/${erreur.status}`, { skipLocationChange: true });
      }
      return throwError(() => erreur);
    }),
  );
}
