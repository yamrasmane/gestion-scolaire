import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MtxSelectModule } from '@ng-matero/extensions/select';

import { AnneeContexte } from '@core/annee-contexte';
import { ApiService, Classe, Impaye, Recouvrement, messageErreur } from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';
import { depuisIso, enIso } from '@shared/composants/dates';
import { EtatListe } from '@shared/composants/etat-liste';

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
          <form class="barre-filtres">
            <mat-form-field class="filtre-recherche">
              <mat-label>Rechercher</mat-label>
              <input
                matInput
                name="recherche"
                autocomplete="off"
                placeholder="Matricule, nom, classe, frais"
                [ngModel]="recherche()"
                (ngModelChange)="chercher($event)"
              />
              <mat-icon matSuffix>search</mat-icon>
            </mat-form-field>

            <mat-form-field class="filtre">
              <mat-label>Classe</mat-label>
              <mtx-select
                name="classe"
                [ngModel]="classeId()"
                (ngModelChange)="filtrerClasse($event)"
                [items]="classes()"
                bindValue="id"
                bindLabel="nom"
                placeholder="Toutes"
                notFoundText="Aucune classe ne correspond"
                [searchable]="true"
              />
            </mat-form-field>

            <mat-form-field class="filtre">
              <mat-label>Échéances jusqu'au</mat-label>
              <input
                matInput
                name="limite"
                [matDatepicker]="calendrierLimite"
                [ngModel]="dateLimite()"
                (ngModelChange)="filtrerLimite($event)"
              />
              <mat-datepicker-toggle matIconSuffix [for]="calendrierLimite" />
              <mat-datepicker #calendrierLimite />
            </mat-form-field>

            @if (filtresActifs()) {
              <button matButton type="button" class="bouton-filtre" (click)="viderFiltres()">
                <mat-icon>filter_alt_off</mat-icon>
                Effacer
              </button>
            }
            <button
              matButton="tonal"
              type="button"
              class="bouton-filtre"
              [disabled]="liste.total() === 0 || export.enCours"
              (click)="exporter()"
            >
              <mat-icon>download</mat-icon>
              Exporter en CSV
            </button>
          </form>

          @if (impayes().length === 0) {
            <p class="text-muted">
              @if (filtresActifs()) {
                Aucune échéance ne correspond à cette recherche.
              } @else {
                Aucune échéance échue non soldée. Tout est à jour.
              }
            </p>
          } @else {
            <p>
              <strong>{{ liste.total() }}</strong> échéance(s) en souffrance, dont
              <strong>{{ total() | number }} FCFA</strong> sur cette page. L'export CSV reprend
              toutes les lignes de la recherche.
            </p>
            <div class="table-defilement">
              <table mat-table [dataSource]="impayes()" class="w-full">
                <ng-container matColumnDef="eleve">
                  <th mat-header-cell *matHeaderCellDef>Élève</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Élève">
                    <strong>{{ i.nom }}</strong> {{ i.prenom }}
                    <span class="text-muted">({{ i.matricule }})</span>
                  </td>
                </ng-container>

                <ng-container matColumnDef="classe">
                  <th mat-header-cell *matHeaderCellDef>Classe</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Classe">{{ i.classeNom }}</td>
                </ng-container>

                <ng-container matColumnDef="frais">
                  <th mat-header-cell *matHeaderCellDef>Frais</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Frais">
                    {{ i.typeFraisLibelle }}
                  </td>
                </ng-container>

                <ng-container matColumnDef="tranche">
                  <th mat-header-cell *matHeaderCellDef>Tranche</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Tranche">
                    {{ i.trancheLibelle }}
                  </td>
                </ng-container>

                <ng-container matColumnDef="echeance">
                  <th mat-header-cell *matHeaderCellDef>Échéance</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Échéance">
                    {{ i.dateEcheance | date: 'dd/MM/yyyy' }}
                  </td>
                </ng-container>

                <ng-container matColumnDef="reste">
                  <th mat-header-cell *matHeaderCellDef>Reste dû</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Reste dû">
                    <strong>{{ i.reste | number }}</strong>
                  </td>
                </ng-container>

                <ng-container matColumnDef="statut">
                  <th mat-header-cell *matHeaderCellDef>Statut</th>
                  <td mat-cell *matCellDef="let i" data-libelle="Statut">
                    <mat-chip>{{ i.statut === 'EN_RETARD' ? 'En retard' : 'Partiel' }}</mat-chip>
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
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatPaginatorModule,
    MtxSelectModule,
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
  protected readonly liste = new EtatListe(25);
  protected readonly recherche = signal('');
  protected readonly limite = signal<string | null>(null);
  protected readonly export = { enCours: false };

  /** Somme des restes dus de la page affichée. */
  protected readonly total = computed(() =>
    this.impayes().reduce((somme, i) => somme + i.reste, 0),
  );

  protected readonly dateLimite = () => depuisIso(this.limite());

  protected readonly filtresActifs = () =>
    this.recherche() !== '' || this.classeId() !== null || this.limite() !== null;

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
      this.charger();
    });
  }

  protected chercher(terme: string): void {
    this.recherche.set(terme);
    this.liste.differer(() => {
      this.liste.auDebut();
      this.charger();
    });
  }

  protected filtrerClasse(classeId: string | null): void {
    this.classeId.set(classeId);
    this.liste.auDebut();
    this.charger();
  }

  protected filtrerLimite(limite: Date | null): void {
    this.limite.set(enIso(limite));
    this.liste.auDebut();
    this.charger();
  }

  protected viderFiltres(): void {
    this.recherche.set('');
    this.classeId.set(null);
    this.limite.set(null);
    this.liste.auDebut();
    this.charger();
  }

  /**
   * Export CSV, ouvrable dans Excel (SF-31). La liste étant paginée, on
   * redemande à l'API toutes les lignes qui répondent à la recherche.
   */
  protected exporter(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId) {
      return;
    }
    this.export.enCours = true;
    this.api.impayes(anneeId, { ...this.criteres(), page: 0, taille: 5000 }).subscribe({
      next: (page) => {
        this.ecrireCsv(page.contenu);
        this.export.enCours = false;
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.export.enCours = false;
      },
    });
  }

  private ecrireCsv(lignesImpayees: Impaye[]): void {
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
    const lignes = lignesImpayees.map((i) =>
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

  protected charger(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId) {
      return;
    }
    this.liste.chargement.set(true);
    this.api.impayes(anneeId, { ...this.liste.parametres(), ...this.criteres() }).subscribe({
      next: (page) => {
        this.impayes.set(this.liste.recevoir(page));
        this.liste.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.liste.chargement.set(false);
      },
    });
  }

  private criteres() {
    return { classeId: this.classeId(), limite: this.limite(), recherche: this.recherche() };
  }
}
