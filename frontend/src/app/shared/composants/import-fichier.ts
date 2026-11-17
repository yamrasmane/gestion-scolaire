import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { ApiService, RapportImport, messageErreur } from '@core/api.service';

/**
 * Import Excel/CSV en deux étapes : « Analyser » affiche l'aperçu et les
 * erreurs sans rien enregistrer, puis « Importer » enregistre si tout est valide.
 */
@Component({
  selector: 'app-import-fichier',
  template: `
    @if (aide()) {
      <p class="text-muted m-t-0">{{ aide() }}</p>
    }

    <div class="d-flex align-items-center flex-wrap gap-8">
      <button matButton="outlined" (click)="parcourir.click()">
        <mat-icon>attach_file</mat-icon>
        {{ fichier()?.name ?? 'Choisir un fichier' }}
      </button>
      <input
        #parcourir
        class="d-none"
        type="file"
        accept=".xlsx,.xls,.csv"
        aria-label="Fichier à importer"
        (change)="choisir($event)"
      />
      <button matButton="tonal" [disabled]="!fichier() || enCours()" (click)="envoyer(true)">
        Analyser
      </button>
      <button matButton="filled" [disabled]="!importable() || enCours()" (click)="envoyer(false)">
        Importer
      </button>
    </div>

    @if (enCours()) {
      <mat-progress-bar class="m-t-16" mode="indeterminate" />
    }

    @if (erreur(); as message) {
      <p class="alerte-erreur" role="alert">{{ message }}</p>
    }

    @if (rapport(); as r) {
      @if (r.importe) {
        <p class="alerte-succes">{{ r.totalLignes }} ligne(s) enregistrée(s).</p>
      } @else if (r.erreurs.length > 0) {
        <p class="alerte-erreur">
          {{ r.erreurs.length }} erreur(s) : rien n'a été importé. Corrigez le fichier puis relancez
          l'analyse.
        </p>
        <table mat-table [dataSource]="r.erreurs" class="w-full">
          <ng-container matColumnDef="ligne">
            <th mat-header-cell *matHeaderCellDef>Ligne</th>
            <td mat-cell *matCellDef="let e">{{ e.ligne }}</td>
          </ng-container>
          <ng-container matColumnDef="message">
            <th mat-header-cell *matHeaderCellDef>Problème</th>
            <td mat-cell *matCellDef="let e">{{ e.message }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="colonnesErreur"></tr>
          <tr mat-row *matRowDef="let ligne; columns: colonnesErreur"></tr>
        </table>
      } @else {
        <p class="alerte-succes">
          {{ r.totalLignes }} ligne(s) prête(s). Cliquez sur « Importer » pour les enregistrer.
        </p>
      }

      @for (a of r.avertissements; track $index) {
        <p class="text-muted">{{ a }}</p>
      }

      @if (!r.importe && r.lignes.length > 0) {
        <mat-accordion class="m-t-16">
          <mat-expansion-panel>
            <mat-expansion-panel-header>
              <mat-panel-title>Aperçu des {{ r.lignes.length }} ligne(s) lues</mat-panel-title>
            </mat-expansion-panel-header>
            <div class="table-defilement">
              <table mat-table [dataSource]="r.lignes" class="w-full">
                @for (c of colonnes(); track c) {
                  <ng-container [matColumnDef]="c">
                    <th mat-header-cell *matHeaderCellDef>{{ c }}</th>
                    <td mat-cell *matCellDef="let l">{{ afficher(l[c]) }}</td>
                  </ng-container>
                }
                <tr mat-header-row *matHeaderRowDef="colonnes()"></tr>
                <tr mat-row *matRowDef="let ligne; columns: colonnes()"></tr>
              </table>
            </div>
          </mat-expansion-panel>
        </mat-accordion>
      }
    }
  `,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatExpansionModule,
    MatProgressBarModule,
  ],
})
export class ImportFichier {
  private readonly api = inject(ApiService);

  /** Chemin de l'API d'import, par exemple `/personnel/import`. */
  readonly chemin = input.required<string>();
  /** Rappel des colonnes attendues. */
  readonly aide = input('');
  /** Émis quand des lignes ont été enregistrées. */
  readonly termine = output<void>();

  protected readonly colonnesErreur = ['ligne', 'message'];
  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');

  /** L'import n'est proposé qu'après une analyse sans erreur du fichier choisi. */
  protected readonly importable = computed(() => {
    const r = this.rapport();
    return !!this.fichier() && !!r && r.simulation && r.erreurs.length === 0;
  });
  protected readonly colonnes = computed(() => Object.keys(this.rapport()?.lignes[0] ?? {}));

  protected choisir(evenement: Event): void {
    this.fichier.set((evenement.target as HTMLInputElement).files?.[0] ?? null);
    this.rapport.set(null);
    this.erreur.set('');
  }

  protected envoyer(simulation: boolean): void {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.api.importer(this.chemin(), fichier, simulation).subscribe({
      next: (rapport) => {
        this.rapport.set(rapport);
        this.enCours.set(false);
        if (rapport.importe) {
          this.termine.emit();
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected afficher(valeur: string | number | boolean): string {
    return typeof valeur === 'boolean' ? (valeur ? 'oui' : 'non') : String(valeur ?? '');
  }
}
