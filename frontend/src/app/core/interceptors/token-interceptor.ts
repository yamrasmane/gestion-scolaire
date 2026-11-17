import { HttpHandlerFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';

import { AuthService } from '@core/authentication';
import { config } from '@core/config';

/** Ajoute le jeton Keycloak aux appels vers l'API (et uniquement vers elle). */
export function tokenInterceptor(requete: HttpRequest<unknown>, suivant: HttpHandlerFn) {
  if (!requete.url.startsWith(config.apiUrl)) {
    return suivant(requete);
  }

  const auth = inject(AuthService);
  return from(auth.jeton()).pipe(
    switchMap((jeton) =>
      suivant(
        jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete,
      ),
    ),
  );
}
