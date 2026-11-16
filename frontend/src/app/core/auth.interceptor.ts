import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { config } from './config';

/** Ajoute le jeton Keycloak aux appels vers l'API (et uniquement vers elle). */
export const authInterceptor: HttpInterceptorFn = (requete, suivant) => {
  if (!requete.url.startsWith(config.apiUrl)) {
    return suivant(requete);
  }
  const auth = inject(AuthService);
  return from(auth.jeton()).pipe(
    switchMap((jeton) =>
      suivant(jeton ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } }) : requete),
    ),
  );
};
