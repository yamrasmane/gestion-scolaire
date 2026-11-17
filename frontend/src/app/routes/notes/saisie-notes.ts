import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  CategorieEvaluation,
  Classe,
  Enseignement,
  Grille,
  Trimestre,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';

/** Grille de saisie des notes, par matière et par trimestre (SF-11, SF-12). */
@Component({
  selector: 'app-saisie-notes',
  templateUrl: './saisie-notes.html',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTooltipModule,
    PageHeader,
    AnneeRequise,
  ],
})
export class SaisieNotes {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly classes = signal<Classe[]>([]);
  protected readonly trimestres = signal<Trimestre[]>([]);
  protected readonly enseignements = signal<Enseignement[]>([]);
  protected readonly classeId = signal<string | null>(null);
  protected readonly trimestreId = signal<string | null>(null);
  protected readonly enseignementId = signal<string | null>(null);
  protected readonly grille = signal<Grille | null>(null);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  /** Saisies en cours : clé « inscription-évaluation ». */
  protected valeurs: Record<string, { valeur: number | null; absent: boolean }> = {};

  protected nouvelleEvaluation = this.videEvaluation();

  protected readonly modifiable = computed(() => !this.grille()?.trimestreCloture);

  constructor() {
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.reinitialiser();
      if (!anneeId) {
        this.classes.set([]);
        this.trimestres.set([]);
        return;
      }
      this.api.classes(anneeId).subscribe({
        next: (classes) => this.classes.set(classes),
        error: (e) => this.erreur.set(messageErreur(e)),
      });
      this.api.trimestres(anneeId).subscribe({
        next: (trimestres) => {
          this.trimestres.set(trimestres);
          const ouvert = trimestres.find((t) => !t.cloture) ?? trimestres[0];
          this.trimestreId.set(ouvert?.id ?? null);
        },
        error: (e) => this.erreur.set(messageErreur(e)),
      });
    });
  }

  protected choisirClasse(id: string): void {
    this.classeId.set(id);
    this.enseignementId.set(null);
    this.grille.set(null);
    this.api.enseignements(id).subscribe({
      next: (enseignements) => this.enseignements.set(enseignements),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected choisirMatiere(id: string): void {
    this.enseignementId.set(id);
    this.charger();
  }

  protected choisirTrimestre(id: string): void {
    this.trimestreId.set(id);
    if (this.enseignementId()) {
      this.charger();
    }
  }

  protected cle(inscriptionId: string, evaluationId: string): string {
    return `${inscriptionId}-${evaluationId}`;
  }

  protected saisie(inscriptionId: string, evaluationId: string) {
    const cle = this.cle(inscriptionId, evaluationId);
    this.valeurs[cle] ??= { valeur: null, absent: false };
    return this.valeurs[cle];
  }

  protected creerEvaluation(): void {
    const enseignement = this.enseignementId();
    const trimestre = this.trimestreId();
    if (!enseignement || !trimestre || !this.nouvelleEvaluation.libelle.trim()) {
      return;
    }
    this.executer(
      this.api.creerEvaluation(enseignement, trimestre, {
        categorie: this.nouvelleEvaluation.categorie,
        libelle: this.nouvelleEvaluation.libelle,
        dateEval: this.nouvelleEvaluation.dateEval || null,
      }),
      () => (this.nouvelleEvaluation = this.videEvaluation()),
    );
  }

  protected supprimerEvaluation(evaluationId: string): void {
    this.executer(this.api.supprimerEvaluation(evaluationId));
  }

  protected enregistrer(): void {
    const g = this.grille();
    const enseignement = this.enseignementId();
    const trimestre = this.trimestreId();
    if (!g || !enseignement || !trimestre) {
      return;
    }
    const notes = [];
    for (const ligne of g.lignes) {
      for (const evaluation of g.evaluations) {
        const saisie = this.saisie(ligne.inscriptionId, evaluation.id);
        if (saisie.valeur === null && !saisie.absent) {
          continue;
        }
        notes.push({
          inscriptionId: ligne.inscriptionId,
          evaluationId: evaluation.id,
          valeur: saisie.absent ? null : saisie.valeur,
          absent: saisie.absent,
          appreciation: null,
        });
      }
    }
    this.executer(this.api.enregistrerGrille(enseignement, trimestre, notes), () =>
      this.information.set('Notes enregistrées.'),
    );
  }

  private executer(action: Observable<unknown>, apres?: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    action.subscribe({
      next: () => {
        apres?.();
        this.enCours.set(false);
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private charger(): void {
    const enseignement = this.enseignementId();
    const trimestre = this.trimestreId();
    if (!enseignement || !trimestre) {
      return;
    }
    this.api.grille(enseignement, trimestre).subscribe({
      next: (grille) => {
        this.grille.set(grille);
        this.valeurs = {};
        for (const ligne of grille.lignes) {
          for (const note of ligne.notes) {
            this.valeurs[this.cle(ligne.inscriptionId, note.evaluationId)] = {
              valeur: note.valeur,
              absent: note.absent,
            };
          }
        }
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private reinitialiser(): void {
    this.classeId.set(null);
    this.enseignementId.set(null);
    this.grille.set(null);
    this.enseignements.set([]);
  }

  private videEvaluation(): { categorie: CategorieEvaluation; libelle: string; dateEval: string } {
    return { categorie: 'DEVOIR', libelle: '', dateEval: '' };
  }
}
