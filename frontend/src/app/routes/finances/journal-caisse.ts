import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';

import {
  ApiService,
  JournalCaisse as Journal,
  ModePaiement,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';

/** Journal de caisse quotidien (SF-32). */
@Component({
  selector: 'app-journal-caisse',
  template: `
    <page-header title="Journal de caisse" />

    @if (erreur(); as message) {
      <p class="alerte-erreur" role="alert">{{ message }}</p>
    }

    <mat-card>
      <mat-card-content>
        <div class="ligne-actions">
          <mat-form-field>
            <mat-label>Journée</mat-label>
            <input matInput type="date" [ngModel]="journee()" (ngModelChange)="choisir($event)" />
          </mat-form-field>
          <button matButton="tonal" (click)="imprimer()">
            <mat-icon>print</mat-icon>
            Imprimer
          </button>
        </div>

        @if (journal(); as j) {
          <div class="resume-finances">
            <div>
              <span class="libelle">Total encaissé</span>
              <span class="valeur">{{ j.total | number }} FCFA</span>
            </div>
            @for (t of j.totauxParMode; track t.mode) {
              <div>
                <span class="libelle">{{ libelleMode(t.mode) }}</span>
                <span class="valeur">{{ t.total | number }} FCFA</span>
              </div>
            }
          </div>

          @if (j.paiements.length === 0) {
            <p class="text-muted">Aucun encaissement ce jour-là.</p>
          } @else {
            <div class="table-defilement">
              <table mat-table [dataSource]="j.paiements" class="w-full">
                <ng-container matColumnDef="recu">
                  <th mat-header-cell *matHeaderCellDef>Reçu</th>
                  <td mat-cell *matCellDef="let p">{{ p.numeroRecu }}</td>
                </ng-container>

                <ng-container matColumnDef="montant">
                  <th mat-header-cell *matHeaderCellDef>Montant</th>
                  <td mat-cell *matCellDef="let p">
                    <strong>{{ p.montant | number }}</strong>
                  </td>
                </ng-container>

                <ng-container matColumnDef="mode">
                  <th mat-header-cell *matHeaderCellDef>Mode</th>
                  <td mat-cell *matCellDef="let p">{{ libelleMode(p.mode) }}</td>
                </ng-container>

                <ng-container matColumnDef="reference">
                  <th mat-header-cell *matHeaderCellDef>Référence</th>
                  <td mat-cell *matCellDef="let p">{{ p.reference }}</td>
                </ng-container>

                <ng-container matColumnDef="payeur">
                  <th mat-header-cell *matHeaderCellDef>Payeur</th>
                  <td mat-cell *matCellDef="let p">{{ p.payeur }}</td>
                </ng-container>

                <ng-container matColumnDef="caissier">
                  <th mat-header-cell *matHeaderCellDef>Caissier</th>
                  <td mat-cell *matCellDef="let p">{{ p.caissier }}</td>
                </ng-container>

                <ng-container matColumnDef="statut">
                  <th mat-header-cell *matHeaderCellDef>Statut</th>
                  <td mat-cell *matCellDef="let p">
                    @if (p.statut === 'ANNULE') {
                      <mat-chip>Annulé</mat-chip>
                    } @else {
                      <mat-chip highlighted>Valide</mat-chip>
                    }
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
                <tr mat-row *matRowDef="let ligne; columns: colonnes"></tr>
              </table>
            </div>
            <p class="text-muted">
              Les paiements annulés restent au journal, sans compter dans le total.
            </p>
          }
        }
      </mat-card-content>
    </mat-card>
  `,
  imports: [
    FormsModule,
    DecimalPipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    PageHeader,
  ],
})
export class JournalCaisse {
  private readonly api = inject(ApiService);

  protected readonly colonnes = [
    'recu',
    'montant',
    'mode',
    'reference',
    'payeur',
    'caissier',
    'statut',
  ];
  protected readonly journee = signal(new Date().toISOString().slice(0, 10));
  protected readonly journal = signal<Journal | null>(null);
  protected readonly erreur = signal('');

  constructor() {
    this.charger();
  }

  protected choisir(journee: string): void {
    this.journee.set(journee);
    this.charger();
  }

  protected libelleMode(mode: ModePaiement): string {
    return (
      {
        ESPECES: 'Espèces',
        MOBILE_MONEY: 'Mobile Money',
        VIREMENT: 'Virement',
        CHEQUE: 'Chèque',
      } as Record<ModePaiement, string>
    )[mode];
  }

  protected imprimer(): void {
    window.print();
  }

  private charger(): void {
    this.api.journalCaisse(this.journee()).subscribe({
      next: (journal) => this.journal.set(journal),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
