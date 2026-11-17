import { Component, inject } from '@angular/core';

import { AuthService } from '@core/authentication';

/**
 * Le menu ne montre que ce que le rôle de la personne autorise. Sans aucun
 * rôle, elle verrait un menu amputé sans savoir pourquoi : ce bandeau le dit.
 */
@Component({
  selector: 'app-avis-roles',
  template: `
    @if (auth.check() && auth.roles().length === 0) {
      <p class="alerte-erreur" role="alert">
        Aucun rôle n'est attribué à votre compte « {{ auth.nomUtilisateur() }} » : les menus
        Finances, Personnel et le paramétrage restent masqués, et l'API refusera vos demandes.
        Faites attribuer un rôle à ce compte dans Keycloak (Users → Role mapping), puis
        déconnectez-vous et reconnectez-vous pour que le jeton le porte.
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
      padding: 0 1rem;
    }
  `,
})
export class AvisRoles {
  protected readonly auth = inject(AuthService);
}
