import { Injectable, inject } from '@angular/core';
import { NgxPermissionsService } from 'ngx-permissions';

import { AnneeContexte } from '@core/annee-contexte';
import { AuthService } from '@core/authentication';
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
    this.permissions.loadPermissions(this.auth.roles());

    const menu = structuredClone(MENU_LYCEE);
    this.menuService.addNamespace(menu, 'menu');
    this.menuService.set(menu);

    this.annees.charger();

    return Promise.resolve();
  }
}
