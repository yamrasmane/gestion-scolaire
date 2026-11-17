import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ApiService, ModePaiement, Recu as RecuVue, messageErreur } from '@core/api.service';

/**
 * Reçu imprimable (SF-30). Le navigateur se charge de l'impression ou de
 * l'enregistrement en PDF ; la mise en page est prévue pour une demi-page A4.
 */
@Component({
  selector: 'app-recu',
  template: `
    @if (erreur(); as message) {
      <p class="alerte-erreur">{{ message }}</p>
    }

    @if (recu(); as r) {
      <div class="feuille">
        <div class="barre-outils">
          <button matButton="filled" (click)="imprimer()">
            <mat-icon>print</mat-icon>
            Imprimer
          </button>
        </div>

        <article class="recu">
          <header>
            <div>
              <h1>Reçu de paiement</h1>
              <p class="numero">{{ r.numeroRecu }}</p>
            </div>
            <div class="mentions">
              @if (r.duplicata) {
                <p class="mention">Duplicata</p>
              }
              @if (r.statut === 'ANNULE') {
                <p class="mention annule">Annulé</p>
              }
            </div>
          </header>

          <table class="identite">
            <tr>
              <th>Élève</th>
              <td>{{ r.nom }} {{ r.prenom }}</td>
              <th>Matricule</th>
              <td>{{ r.matricule }}</td>
            </tr>
            <tr>
              <th>Classe</th>
              <td>{{ r.classeNom }}</td>
              <th>Date</th>
              <td>{{ r.datePaiement | date: 'dd/MM/yyyy' }}</td>
            </tr>
            <tr>
              <th>Payeur</th>
              <td>{{ r.payeur || '—' }}</td>
              <th>Mode</th>
              <td>{{ libelleMode(r.mode) }}{{ r.reference ? ' — ' + r.reference : '' }}</td>
            </tr>
          </table>

          <h2>Détail du règlement</h2>
          <table class="detail">
            <thead>
              <tr>
                <th>Frais</th>
                <th>Tranche</th>
                <th class="montant">Montant</th>
              </tr>
            </thead>
            <tbody>
              @for (i of r.imputations; track $index) {
                <tr>
                  <td>{{ i.typeFraisLibelle }}</td>
                  <td>{{ i.trancheLibelle }}</td>
                  <td class="montant">{{ i.montant | number }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="3">Paiement annulé : aucune imputation.</td>
                </tr>
              }
            </tbody>
            <tfoot>
              <tr>
                <th colspan="2">Total versé</th>
                <th class="montant">{{ r.montant | number }} FCFA</th>
              </tr>
            </tfoot>
          </table>

          <p class="lettres">Arrêté le présent reçu à la somme de {{ r.montantEnLettres }}.</p>
          <p>
            Reste à payer après ce règlement : <strong>{{ r.resteAPayer | number }} FCFA</strong>
          </p>

          <footer>
            <div>Caissier : {{ r.caissier || '—' }}</div>
            <div class="signature">Signature et cachet</div>
          </footer>
        </article>
      </div>
    }
  `,
  styles: `
    .feuille {
      max-width: 50rem;
      margin: 0 auto;
      padding: 1.5rem;
      font-family: Roboto, sans-serif;
      color: #000;
      background: #fff;
    }

    .recu {
      padding: 1.5rem;
      border: 1px solid #999;
    }

    header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      padding-bottom: 0.5rem;
      border-bottom: 2px solid #000;
    }

    h1 {
      margin: 0;
      font-size: 1.25rem;
    }

    h2 {
      margin: 1.5rem 0 0.5rem;
      font-size: 1rem;
    }

    .numero {
      margin: 0.25rem 0 0;
      font-size: 1rem;
      font-weight: 500;
    }

    .mention {
      margin: 0;
      padding: 0.25rem 0.75rem;
      border: 2px solid #666;
      font-weight: 700;
      text-transform: uppercase;
    }

    .mention.annule {
      color: #b00;
      border-color: #b00;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    .identite {
      margin-top: 1rem;
    }

    .identite th,
    .identite td {
      padding: 0.25rem 0.5rem;
      text-align: left;
      vertical-align: top;
    }

    .identite th {
      width: 6rem;
      font-weight: 500;
      color: #444;
    }

    .detail th,
    .detail td {
      padding: 0.4rem 0.5rem;
      text-align: left;
      border-bottom: 1px solid #ccc;
    }

    .detail .montant {
      text-align: right;
    }

    .lettres {
      margin-top: 1rem;
      font-style: italic;
    }

    footer {
      display: flex;
      justify-content: space-between;
      margin-top: 3rem;
    }

    .signature {
      padding-top: 3rem;
      border-top: 1px solid #999;
    }

    @media print {
      .barre-outils {
        display: none;
      }

      .feuille {
        padding: 0;
      }

      .recu {
        border: none;
      }
    }
  `,
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatIconModule],
})
export class Recu {
  private readonly api = inject(ApiService);

  /** Identifiant du paiement, lié au paramètre de route. */
  readonly paiementId = input.required<string>();
  readonly duplicata = input<string | undefined>(undefined);

  protected readonly recu = signal<RecuVue | null>(null);
  protected readonly erreur = signal('');

  constructor() {
    queueMicrotask(() => {
      this.api.recu(this.paiementId(), this.duplicata() === 'true').subscribe({
        next: (recu) => this.recu.set(recu),
        error: (e) => this.erreur.set(messageErreur(e)),
      });
    });
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
}
