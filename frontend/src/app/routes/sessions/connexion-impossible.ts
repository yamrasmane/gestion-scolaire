import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

import { AuthService } from '@core/authentication';

/**
 * Affichée quand le serveur de connexion (Keycloak) n'a pas pu être joint :
 * mieux vaut une explication qu'un écran de chargement sans fin.
 */
@Component({
  selector: 'app-connexion-impossible',
  template: `
    <div class="enveloppe">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Connexion impossible</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p>Le serveur de connexion n'a pas répondu. Vérifiez qu'il est démarré :</p>
          <p><code>{{ auth.adresseServeur }}</code></p>
          <p class="text-muted">
            En développement, il s'agit du conteneur Keycloak :
            <code>docker compose up -d keycloak</code>
          </p>
        </mat-card-content>
        <mat-card-actions>
          <button matButton="filled" (click)="auth.reconnecter()">
            <mat-icon>refresh</mat-icon>
            Réessayer
          </button>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: `
    .enveloppe {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 1rem;
    }

    mat-card {
      max-width: 32rem;
    }
  `,
  imports: [MatCardModule, MatButtonModule, MatIconModule],
})
export class ConnexionImpossible {
  protected readonly auth = inject(AuthService);
}
