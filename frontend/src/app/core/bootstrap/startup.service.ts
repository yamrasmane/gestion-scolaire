import { Injectable, inject } from '@angular/core';
import { NgxPermissionsService } from 'ngx-permissions';

import { AnneeContexte } from '@core/annee-contexte';
import { AuthService } from '@core/authentication';
import { TOUS_LES_ROLES } from '@core/roles';
import { MENU_LYCEE } from './menu-lycee';
import { MenuService } from './menu.service';

/**
 * Chargé après l'ouverture de la session Keycloak : pose les rôles comme
 * permissions (le menu s'adapte), publie le menu et charge les années scolaires.
 */
@Injectable({ providedIn: 'root' })
export class StartupService {
  private readonly auth = inject(AuthService);
  private readonly menuService = inject(MenuService);
  private readonly permissions = inject(NgxPermissionsService);
  private readonly annees = inject(AnneeContexte);

  load() {
    // L'administrateur reçoit tous les rôles comme permissions : il voit donc
    // l'intégralité du menu, y compris les entrées ajoutées plus tard sans
    // qu'il ait fallu penser à y inscrire ADMIN.
    const roles = this.auth.roles();
    this.permissions.loadPermissions(roles.includes('ADMIN') ? TOUS_LES_ROLES : roles);

    const menu = structuredClone(MENU_LYCEE);
    this.menuService.addNamespace(menu, 'menu');
    this.menuService.set(menu);

    this.annees.charger();

    return Promise.resolve();
  }
}
