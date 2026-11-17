import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { AnneeContexte } from '@core/annee-contexte';
import { ApiService, Classe, Impaye, Recouvrement, messageErreur } from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';

/** Liste des échéances échues non soldées et tableau de bord (SF-31, SF-32). */
@Component({
  selector: 'app-impayes',
  template: `
    <page-header title="Impayés" [subtitle]="contexte.annee()?.libelle ?? ''" />

    @if (!contexte.annee()) {
      <app-annee-requise />
    } @else {
      @if (erreur(); as message) {
        <p class="alerte-erreur" role="alert">{{ message }}</p>
      }

      @if (recouvrement(); as r) {
        <mat-card>
          <mat-card-header>
            <mat-card-title>Recouvrement de l'année</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="resume-finances">
              <div>
                <span class="libelle">Attendu</span>
                <span class="valeur">{{ r.attendu | number }} FCFA</span>
              </div>
              <div>
                <span class="libelle">Encaissé</span>
                <span class="valeur">{{ r.encaisse | number }} FCFA</span>
              </div>
              <div>
                <span class="libelle">Reste</span>
                <span class="valeur">{{ r.reste | number }} FCFA</span>
              </div>
              <div>
                <span class="libelle">Échu non payé</span>
                <span class="valeur" [class.en-retard]="r.echuNonPaye > 0">
                  {{ r.echuNonPaye | number }} FCFA
                </span>
              </div>
              <div>
                <span class="libelle">Élèves à jour</span>
                <span class="valeur">{{ r.elevesAJour }}</span>
              </div>
              <div>
                <span class="libelle">Élèves en retard</span>
                <span class="valeur" [class.en-retard]="r.elevesEnRetard > 0">
                  {{ r.elevesEnRetard }}
                </span>
              </div>
            </div>
          </mat-card-content>
        </mat-card>
      }

      <mat-card>
        <mat-card-content>
          <div class="ligne-actions">
            <mat-form-field>
              <mat-label>Classe</mat-label>
              <mat-select [ngModel]="classeId()" (ngModelChange)="filtrer($event)">
                <mat-option [value]="null">Toutes les classes</mat-option>
                @for (c of classes(); track c.id) {
                  <mat-option [value]="c.id">{{ c.nom }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button matButton="tonal" (click)="exporter()" [disabled]="impayes().length === 0">
              <mat-icon>download</mat-icon>
              Exporter en CSV
            </button>
          </div>

          @if (impayes().length === 0) {
            <p class="text-muted">Aucune échéance échue non soldée. Tout est à jour.</p>
          } @else {
            <p>
              <strong>{{ impayes().length }}</strong> échéance(s) en souffrance, soit
              <strong>{{ total() | number }} FCFA</strong>.
            </p>
            <div class="table-defilement">
              <table mat-table [dataSource]="impayes()" class="w-full">
                <ng-container matColumnDef="eleve">
                  <th mat-header-cell *matHeaderCellDef>Élève</th>
                  <td mat-cell *matCellDef="let i">
                    <strong>{{ i.nom }}</strong> {{ i.prenom }}
                    <span class="text-muted">({{ i.matricule }})</span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="classe">
                  <th mat-header-cell *matHeaderCellDef>Classe</th>
                  <td mat-cell *matCellDef="let i">{{ i.classeNom }}</td>
                </ng-container>

                <ng-container matColumnDef="frais">
                  <th mat-header-cell *matHeaderCellDef>Frais</th>
                  <td mat-cell *matCellDef="let i">{{ i.typeFraisLibelle }}</td>
                </ng-container>

                <ng-container matColumnDef="tranche">
                  <th mat-header-cell *matHeaderCellDef>Tranche</th>
                  <td mat-cell *matCellDef="let i">{{ i.trancheLibelle }}</td>
                </ng-container>

                <ng-container matColumnDef="echeance">
                  <th mat-header-cell *matHeaderCellDef>Échéance</th>
                  <td mat-cell *matCellDef="let i">{{ i.dateEcheance | date: 'dd/MM/yyyy' }}</td>
                </ng-container>

                <ng-container matColumnDef="reste">
                  <th mat-header-cell *matHeaderCellDef>Reste dû</th>
                  <td mat-cell *matCellDef="let i">
                    <strong>{{ i.reste | number }}</strong>
                  </td>
                </ng-container>

                <ng-container matColumnDef="statut">
                  <th mat-header-cell *matHeaderCellDef>Statut</th>
                  <td mat-cell *matCellDef="let i">
                    <mat-chip>{{ i.statut === 'EN_RETARD' ? 'En retard' : 'Partiel' }}</mat-chip>
                  </td>
                </ng-container>

                <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
                <tr mat-row *matRowDef="let ligne; columns: colonnes"></tr>
              </table>
            </div>
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  imports: [
    FormsModule,
    DatePipe,
    DecimalPipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    PageHeader,
    AnneeRequise,
  ],
})
export class Impayes {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = [
    'eleve',
    'classe',
    'frais',
    'tranche',
    'echeance',
    'reste',
    'statut',
  ];
  protected readonly impayes = signal<Impaye[]>([]);
  protected readonly classes = signal<Classe[]>([]);
  protected readonly classeId = signal<string | null>(null);
  protected readonly recouvrement = signal<Recouvrement | null>(null);
  protected readonly erreur = signal('');

  protected readonly total = computed(() =>
    this.impayes().reduce((somme, i) => somme + i.reste, 0),
  );

  constructor() {
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (!anneeId) {
        this.impayes.set([]);
        return;
      }
      this.api.classes(anneeId).subscribe({
        next: (classes) => this.classes.set(classes),
        error: () => undefined,
      });
      this.api.recouvrement(anneeId).subscribe({
        next: (r) => this.recouvrement.set(r),
        error: () => undefined,
      });
      this.charger(anneeId);
    });
  }

  protected filtrer(classeId: string | null): void {
    this.classeId.set(classeId);
    const anneeId = this.contexte.anneeId();
    if (anneeId) {
      this.charger(anneeId);
    }
  }

  /** Export CSV, ouvrable dans Excel (SF-31). */
  protected exporter(): void {
    const entetes = [
      'Matricule',
      'Nom',
      'Prénom',
      'Classe',
      'Frais',
      'Tranche',
      'Échéance',
      'Reste dû',
    ];
    const lignes = this.impayes().map((i) =>
      [
        i.matricule,
        i.nom,
        i.prenom,
        i.classeNom,
        i.typeFraisLibelle,
        i.trancheLibelle,
        i.dateEcheance,
        i.reste,
      ].join(';'),
    );
    const contenu = '﻿' + [entetes.join(';'), ...lignes].join('\n');
    const lien = document.createElement('a');
    lien.href = URL.createObjectURL(new Blob([contenu], { type: 'text/csv;charset=utf-8' }));
    lien.download = `impayes-${this.contexte.annee()?.libelle ?? ''}.csv`;
    lien.click();
    URL.revokeObjectURL(lien.href);
  }

  private charger(anneeId: string): void {
    this.api.impayes(anneeId, this.classeId()).subscribe({
      next: (impayes) => this.impayes.set(impayes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
