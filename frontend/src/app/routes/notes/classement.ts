import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AnneeContexte } from '@core/annee-contexte';
import { ApiService, Classe, LigneClassement, Trimestre, messageErreur } from '@core/api.service';
import { AuthService } from '@core/authentication';
import { PageHeader } from '@shared/components/page-header/page-header';

/** Classement de la classe, délibération et accès aux bulletins (SF-15 à SF-17). */
@Component({
  selector: 'app-classement',
  template: `
    <page-header title="Classement et bulletins" [subtitle]="contexte.annee()?.libelle ?? ''" />

    @if (!contexte.annee()) {
      <p class="text-muted">Créez d'abord une année scolaire.</p>
    } @else {
      <mat-card>
        <mat-card-content>
          <div class="grille-formulaire">
            <mat-form-field>
              <mat-label>Classe</mat-label>
              <mat-select [ngModel]="classeId()" (ngModelChange)="choisirClasse($event)">
                @for (c of classes(); track c.id) {
                  <mat-option [value]="c.id">{{ c.nom }}</mat-option>
                }
              </mat-select>
            </mat-form-field>

            <mat-form-field>
              <mat-label>Trimestre</mat-label>
              <mat-select [ngModel]="trimestreId()" (ngModelChange)="choisirTrimestre($event)">
                @for (t of trimestres(); track t.id) {
                  <mat-option [value]="t.id">
                    Trimestre {{ t.numero }}{{ t.cloture ? ' (clôturé)' : '' }}
                  </mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
        </mat-card-content>
      </mat-card>

      @if (erreur(); as message) {
        <p class="alerte-erreur" role="alert">{{ message }}</p>
      }
      @if (information(); as message) {
        <p class="alerte-succes">{{ message }}</p>
      }

      @if (classeId() && trimestreId()) {
        <mat-card>
          <mat-card-content>
            @if (lignes().length === 0) {
              <p class="text-muted">Aucun élève inscrit dans cette classe.</p>
            } @else {
              <div class="table-defilement">
                <table mat-table [dataSource]="lignes()" class="w-full">
                  <ng-container matColumnDef="rang">
                    <th mat-header-cell *matHeaderCellDef>Rang</th>
                    <td mat-cell *matCellDef="let l">
                      @if (l.rang) {
                        <strong>{{ l.rang }}{{ l.exAequo ? ' ex' : '' }}</strong>
                      } @else {
                        <span class="text-muted">—</span>
                      }
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="eleve">
                    <th mat-header-cell *matHeaderCellDef>Élève</th>
                    <td mat-cell *matCellDef="let l">
                      <strong>{{ l.nom }}</strong> {{ l.prenom }}
                      <span class="text-muted">({{ l.matricule }})</span>
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="moyenne">
                    <th mat-header-cell *matHeaderCellDef>Moyenne générale</th>
                    <td mat-cell *matCellDef="let l">
                      @if (l.moyenneGenerale === null) {
                        <span class="text-muted">Non classé</span>
                      } @else {
                        <strong>{{ l.moyenneGenerale }}</strong> / 20
                      }
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="mention">
                    <th mat-header-cell *matHeaderCellDef>Décision</th>
                    <td mat-cell *matCellDef="let l">
                      @if (l.mention) {
                        <mat-chip>{{ l.mention }}</mat-chip>
                      }
                    </td>
                  </ng-container>

                  <ng-container matColumnDef="actions">
                    <th mat-header-cell *matHeaderCellDef></th>
                    <td mat-cell *matCellDef="let l">
                      @if (l.bloqueImpaye) {
                        <mat-icon
                          color="warn"
                          matTooltip="Remise suspendue : tranches échues impayées"
                        >
                          lock
                        </mat-icon>
                      }
                      <button matButton (click)="ouvrirBulletin(l)">
                        <mat-icon>description</mat-icon>
                        Bulletin
                      </button>
                    </td>
                  </ng-container>

                  <tr mat-header-row *matHeaderRowDef="colonnes"></tr>
                  <tr mat-row *matRowDef="let ligne; columns: colonnes"></tr>
                </table>
              </div>
            }
          </mat-card-content>

          @if (peutDeliberer) {
            <mat-card-actions>
              <button matButton="filled" [disabled]="enCours()" (click)="deliberer()">
                <mat-icon>gavel</mat-icon>
                Enregistrer la délibération
              </button>
              <span class="text-muted m-l-16">
                Fige les moyennes et les rangs du trimestre pour toute la classe.
              </span>
            </mat-card-actions>
          }
        </mat-card>
      }
    }
  `,
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    PageHeader,
  ],
})
export class Classement {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);
  protected readonly peutDeliberer = inject(AuthService).aUnRole('DIRECTEUR', 'CENSEUR');

  protected readonly colonnes = ['rang', 'eleve', 'moyenne', 'mention', 'actions'];
  protected readonly classes = signal<Classe[]>([]);
  protected readonly trimestres = signal<Trimestre[]>([]);
  protected readonly classeId = signal<number | null>(null);
  protected readonly trimestreId = signal<number | null>(null);
  protected readonly lignes = signal<LigneClassement[]>([]);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  constructor() {
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.classeId.set(null);
      this.lignes.set([]);
      if (!anneeId) {
        return;
      }
      this.api.classes(anneeId).subscribe({
        next: (classes) => this.classes.set(classes),
        error: (e) => this.erreur.set(messageErreur(e)),
      });
      this.api.trimestres(anneeId).subscribe({
        next: (trimestres) => {
          this.trimestres.set(trimestres);
          this.trimestreId.set((trimestres.find((t) => !t.cloture) ?? trimestres[0])?.id ?? null);
        },
        error: (e) => this.erreur.set(messageErreur(e)),
      });
    });
  }

  protected choisirClasse(id: number): void {
    this.classeId.set(id);
    this.charger();
  }

  protected choisirTrimestre(id: number): void {
    this.trimestreId.set(id);
    this.charger();
  }

  protected deliberer(): void {
    const classe = this.classeId();
    const trimestre = this.trimestreId();
    if (!classe || !trimestre) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.api.deliberer(classe, trimestre).subscribe({
      next: (lignes) => {
        this.lignes.set(lignes);
        this.information.set('Délibération enregistrée : les bulletins sont arrêtés.');
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected ouvrirBulletin(ligne: LigneClassement): void {
    window.open(`/bulletin/${ligne.inscriptionId}/${this.trimestreId()}`, '_blank');
  }

  private charger(): void {
    const classe = this.classeId();
    const trimestre = this.trimestreId();
    if (!classe || !trimestre) {
      return;
    }
    this.erreur.set('');
    this.api.classement(classe, trimestre).subscribe({
      next: (lignes) => this.lignes.set(lignes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
