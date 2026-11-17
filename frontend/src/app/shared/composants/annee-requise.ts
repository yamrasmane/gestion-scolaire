import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AnneeContexte } from '@core/annee-contexte';

/**
 * Affiché par les pages qui ont besoin d'une année scolaire de travail.
 * Distingue les trois situations, qui demandent trois gestes différents :
 * chargement en cours, API injoignable, ou base réellement sans année.
 */
@Component({
  selector: 'app-annee-requise',
  template: `
    @if (contexte.chargement()) {
      <p class="text-muted">Chargement des années scolaires…</p>
    } @else if (contexte.erreur(); as message) {
      <p class="alerte-erreur" role="alert">
        Les années scolaires n'ont pas pu être chargées : {{ message }}
      </p>
      <p class="text-muted">
        Vérifiez que l'API est démarrée, puis réessayez. Si l'erreur persiste, l'onglet « Réseau »
        du navigateur (touche F12) indique l'appel en échec.
      </p>
      <button matButton="filled" (click)="contexte.charger()">
        <mat-icon>refresh</mat-icon>
        Réessayer
      </button>
    } @else {
      <p class="text-muted">Aucune année scolaire n'existe encore : créez-en une pour commencer.</p>
      <a matButton="filled" routerLink="/parametrage/annees-scolaires">
        <mat-icon>event</mat-icon>
        Créer une année scolaire
      </a>
    }
  `,
  imports: [RouterLink, MatButtonModule, MatIconModule],
})
export class AnneeRequise {
  protected readonly contexte = inject(AnneeContexte);
}
