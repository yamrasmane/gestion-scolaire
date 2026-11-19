import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';

import {
  ApiService,
  JournalCaisse as Journal,
  ModePaiement,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { aujourdhui, enIso } from '@shared/composants/dates';
import { EtatListe } from '@shared/composants/etat-liste';
import { BoutonDocument } from '@shared/composants/bouton-document';

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
            <input
              matInput
              [matDatepicker]="calendrier"
              [ngModel]="journee()"
              (ngModelChange)="choisir($event)"
            />
            <mat-datepicker-toggle matIconSuffix [for]="calendrier" />
            <mat-datepicker #calendrier />
          </mat-form-field>
          <app-bouton-document
            chemin="/journal-caisse"
            [nom]="'journal-' + journeeIso()"
            [criteres]="{ journee: journeeIso() }"
            libelle="Éditer le journal"
          />
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

          @if (j.paiements.contenu.length === 0) {
            <p class="text-muted">Aucun encaissement ce jour-là.</p>
          } @else {
            <div class="table-defilement">
              <table mat-table [dataSource]="j.paiements.contenu" class="w-full">
                <ng-container matColumnDef="recu">
                  <th mat-header-cell *matHeaderCellDef>Reçu</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Reçu">{{ p.numeroRecu }}</td>
                </ng-container>

                <ng-container matColumnDef="montant">
                  <th mat-header-cell *matHeaderCellDef>Montant</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Montant">
                    <strong>{{ p.montant | number }}</strong>
                  </td>
                </ng-container>

                <ng-container matColumnDef="mode">
                  <th mat-header-cell *matHeaderCellDef>Mode</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Mode">
                    {{ libelleMode(p.mode) }}
                  </td>
                </ng-container>

                <ng-container matColumnDef="reference">
                  <th mat-header-cell *matHeaderCellDef>Référence</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Référence">{{ p.reference }}</td>
                </ng-container>

                <ng-container matColumnDef="payeur">
                  <th mat-header-cell *matHeaderCellDef>Payeur</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Payeur">{{ p.payeur }}</td>
                </ng-container>

                <ng-container matColumnDef="caissier">
                  <th mat-header-cell *matHeaderCellDef>Caissier</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Caissier">{{ p.caissier }}</td>
                </ng-container>

                <ng-container matColumnDef="statut">
                  <th mat-header-cell *matHeaderCellDef>Statut</th>
                  <td mat-cell *matCellDef="let p" data-libelle="Statut">
                    @if (p.statut === 'ANNULE') {
                      <mat-chip class="etat-alerte">Annulé</mat-chip>
                    } @else {
                      <mat-chip class="etat-ok">Valide</mat-chip>
                    }
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
                <tr mat-row *matRowDef="let ligne; columns: colonnes"></tr>
              </table>
            </div>
            <mat-paginator
              [length]="liste.total()"
              [pageIndex]="liste.page()"
              [pageSize]="liste.taille()"
              [pageSizeOptions]="liste.taillesPage"
              showFirstLastButtons
              (page)="liste.surPage($event); charger()"
            />
            <p class="text-muted">
              Les paiements annulés restent au journal, sans compter dans le total. Le total et les
              totaux par mode portent sur la journée entière, pas sur la page affichée.
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
    MatDatepickerModule,
    MatPaginatorModule,
    PageHeader,
    BoutonDocument,
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
  /**
   * La journée est gardée sous forme de Date, celle que le calendrier
   * manipule : reconstruire l'objet à chaque affichage relancerait sans fin
   * la détection de changement et figerait la page.
   */
  protected readonly journee = signal<Date>(new Date());
  protected readonly journal = signal<Journal | null>(null);
  protected readonly erreur = signal('');
  /** Les lignes du journal arrivent paginées ; les totaux restent ceux du jour. */
  protected readonly liste = new EtatListe(25);

  constructor() {
    this.charger();
  }

  /** La journée au format de l'API, pour l'édition du document. */
  protected readonly journeeIso = () => enIso(this.journee()) ?? aujourdhui();

  protected choisir(journee: Date | null): void {
    this.journee.set(journee ?? new Date());
    this.liste.auDebut();
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

  protected charger(): void {
    this.liste.chargement.set(true);
    this.api
      .journalCaisse(enIso(this.journee()) ?? aujourdhui(), this.liste.parametres())
      .subscribe({
        next: (journal) => {
          this.journal.set(journal);
          this.liste.recevoir(journal.paiements);
          this.liste.chargement.set(false);
        },
        error: (e) => {
          this.erreur.set(messageErreur(e));
          this.liste.chargement.set(false);
        },
      });
  }
}
