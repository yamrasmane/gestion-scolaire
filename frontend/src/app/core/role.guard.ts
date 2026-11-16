import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './roles';

/** Réserve une route aux rôles indiqués : `canActivate: [roleGuard('INTENDANT')]`. */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => inject(AuthService).aUnRole(...roles) || inject(Router).parseUrl('/acces-refuse');
}
