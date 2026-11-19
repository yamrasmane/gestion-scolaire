import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';

import { ApiService, TypeFrais, messageErreur } from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';

/** Catalogue des frais de l'établissement (SF-21). */
@Component({
  selector: 'app-types-frais',
  template: `
    <page-header title="Types de frais" subtitle="Catalogue des frais de l'établissement" />

    <div class="barre-page">
      <app-bouton-formulaire
        libelle="Ajouter un type de frais"
        formulaire="type-frais-nouveau"
        action="Ajouter"
      >
        <ng-template let-fenetre>
          <form
            id="type-frais-nouveau"
            #formulaire="ngForm"
            (ngSubmit)="formulaire.valid && creer(fenetre)"
          >
            <div class="grille-formulaire">
              <mat-form-field>
                <mat-label>Code</mat-label>
                <input
                  matInput
                  name="code"
                  required
                  maxlength="30"
                  placeholder="CANTINE"
                  [(ngModel)]="nouveau.code"
                />
              </mat-form-field>

              <mat-form-field>
                <mat-label>Libellé</mat-label>
                <input
                  matInput
                  name="libelle"
                  required
                  placeholder="Frais de cantine"
                  [(ngModel)]="nouveau.libelle"
                />
              </mat-form-field>

              <mat-form-field>
                <mat-label>Priorité d'imputation</mat-label>
                <input
                  matInput
                  name="priorite"
                  type="number"
                  min="0"
                  required
                  [(ngModel)]="nouveau.prioriteImputation"
                />
              </mat-form-field>
            </div>

            <div class="ligne-actions m-b-16">
              <mat-checkbox name="obligatoire" [(ngModel)]="nouveau.obligatoire">
                Facturé à tous les élèves
              </mat-checkbox>
              <mat-checkbox name="fractionnable" [(ngModel)]="nouveau.fractionnable">
                Réparti sur les tranches
              </mat-checkbox>
            </div>
          </form>
        </ng-template>
      </app-bouton-formulaire>
    </div>

    @if (erreur(); as message) {
      <p class="alerte-erreur" role="alert">{{ message }}</p>
    }

    <mat-card>
      <mat-card-content>
        <div class="table-defilement">
          <table mat-table [dataSource]="types()" class="w-full">
            <ng-container matColumnDef="code">
              <th mat-header-cell *matHeaderCellDef>Code</th>
              <td mat-cell *matCellDef="let t">
                <strong>{{ t.code }}</strong>
              </td>
            </ng-container>

            <ng-container matColumnDef="libelle">
              <th mat-header-cell *matHeaderCellDef>Libellé</th>
              <td mat-cell *matCellDef="let t">{{ t.libelle }}</td>
            </ng-container>

            <ng-container matColumnDef="obligatoire">
              <th mat-header-cell *matHeaderCellDef>Facturation</th>
              <td mat-cell *matCellDef="let t">
                {{ t.obligatoire ? 'Obligatoire' : 'Sur demande' }}
              </td>
            </ng-container>

            <ng-container matColumnDef="fractionnable">
              <th mat-header-cell *matHeaderCellDef>Étalement</th>
              <td mat-cell *matCellDef="let t">
                {{ t.fractionnable ? 'Réparti sur les tranches' : 'Dû à la 1re tranche' }}
              </td>
            </ng-container>

            <ng-container matColumnDef="priorite">
              <th mat-header-cell *matHeaderCellDef>Priorité</th>
              <td mat-cell *matCellDef="let t">{{ t.prioriteImputation }}</td>
            </ng-container>

            <ng-container matColumnDef="etat">
              <th mat-header-cell *matHeaderCellDef>État</th>
              <td mat-cell *matCellDef="let t">
                @if (t.actif) {
                  <mat-chip class="etat-ok">Actif</mat-chip>
                } @else {
                  <mat-chip class="etat-info">Inactif</mat-chip>
                }
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let t">
                <button matButton (click)="basculer(t)">
                  {{ t.actif ? 'Désactiver' : 'Réactiver' }}
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
            <tr mat-row *matRowDef="let ligne; columns: colonnes"></tr>
          </table>
        </div>
        <p class="text-muted">
          La priorité sert à répartir un règlement entre plusieurs frais : le plus petit nombre est
          servi en premier.
        </p>
      </mat-card-content>
    </mat-card>
  `,
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    PageHeader,
    BoutonFormulaire,
  ],
})
export class TypesFrais {
  private readonly api = inject(ApiService);

  protected readonly colonnes = [
    'code',
    'libelle',
    'obligatoire',
    'fractionnable',
    'priorite',
    'etat',
    'actions',
  ];
  protected readonly types = signal<TypeFrais[]>([]);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected nouveau = this.vide();

  constructor() {
    this.charger();
  }

  /** La fenêtre ne se referme qu'une fois le type de frais créé. */
  protected creer(fenetre?: BoutonFormulaire): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.api.creerTypeFrais(this.nouveau).subscribe({
      next: () => {
        this.nouveau = this.vide();
        this.enCours.set(false);
        fenetre?.fermer();
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected basculer(type: TypeFrais): void {
    this.api.activerTypeFrais(type.id, !type.actif).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private charger(): void {
    this.api.typesFrais().subscribe({
      next: (types) => this.types.set(types),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private vide() {
    return {
      code: '',
      libelle: '',
      obligatoire: true,
      fractionnable: false,
      prioriteImputation: 50,
    };
  }
}
