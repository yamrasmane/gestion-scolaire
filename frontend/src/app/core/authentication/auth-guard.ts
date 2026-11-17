import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '@core/roles';
import { AuthService } from './auth.service';

/**
 * Route réservée aux utilisateurs connectés. Keycloak redirige déjà vers sa
 * page de connexion au démarrage ; cette garde ne sert donc qu'au cas où le
 * serveur de connexion est injoignable.
 */
export const authGuard: CanActivateFn = () =>
  inject(AuthService).check() || inject(Router).parseUrl('/connexion-impossible');

/** Réserve une route aux rôles indiqués : `canActivate: [roleGuard('INTENDANT')]`. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => inject(AuthService).aUnRole(...roles) || inject(Router).parseUrl('/403');
}
