import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPseudoCheckbox } from '@angular/material/core';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AnneeContexte } from '@core/annee-contexte';

/**
 * Année scolaire de travail, choisie dans le bandeau et partagée par toutes
 * les pages. L'année active est repérée par une puce.
 */
@Component({
  selector: 'app-annee-selecteur',
  template: `
    <button mat-button [matMenuTriggerFor]="menu" matTooltip="Année scolaire de travail">
      <mat-icon>event</mat-icon>
      <span>{{ contexte.annee()?.libelle ?? 'Aucune année' }}</span>
      <mat-icon iconPositionEnd>arrow_drop_down</mat-icon>
    </button>

    <mat-menu #menu="matMenu">
      @for (annee of contexte.annees(); track annee.id) {
        <button mat-menu-item (click)="contexte.choisir(annee.id)">
          <span class="d-flex justify-content-between gap-8">
            {{ annee.libelle }}{{ annee.actif ? ' (active)' : '' }}
            @if (annee.id === contexte.anneeId()) {
              <mat-pseudo-checkbox state="checked" appearance="minimal" />
            }
          </span>
        </button>
      } @empty {
        <button mat-menu-item disabled>Aucune année scolaire</button>
      }
    </mat-menu>
  `,
  imports: [MatButtonModule, MatIconModule, MatMenuModule, MatPseudoCheckbox, MatTooltipModule],
})
export class AnneeSelecteur {
  protected readonly contexte = inject(AnneeContexte);
}
