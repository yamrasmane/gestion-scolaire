import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  ChargeEnseignant,
  Classe,
  Enseignement,
  Personnel,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';

/**
 * Affectation des professeurs aux matières d'une classe (SF-10).
 * Les matières viennent du programme du niveau ; on ne nomme ici que
 * les professeurs, pour toute la classe en une seule validation.
 */
@Component({
  selector: 'app-affectations',
  templateUrl: './affectations.html',
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    PageHeader,
    AnneeRequise,
  ],
})
export class Affectations {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['matiere', 'coefficient', 'enseignant'];
  protected readonly colonnesCharge = ['classe', 'matiere', 'coefficient'];
  protected readonly classes = signal<Classe[]>([]);
  protected readonly enseignants = signal<Personnel[]>([]);
  protected readonly enseignements = signal<Enseignement[]>([]);
  protected readonly classeId = signal<string | null>(null);
  protected readonly charge = signal<ChargeEnseignant | null>(null);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  /** Choix en cours, par identifiant d'enseignement : rien n'est envoyé avant validation. */
  protected readonly choix = signal<Record<string, string | null>>({});

  protected readonly matieresSansProfesseur = computed(() => {
    const choix = this.choix();
    return this.enseignements().filter((e) => !choix[e.id]).length;
  });

  protected readonly modifie = computed(() => {
    const choix = this.choix();
    return this.enseignements().some((e) => (choix[e.id] ?? null) !== (e.enseignantId ?? null));
  });

  protected enseignantObserve: string | null = null;

  constructor() {
    this.api.personnel().subscribe({
      next: (personnel) => this.enseignants.set(personnel.filter((p) => p.actif)),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.classeId.set(null);
      this.enseignements.set([]);
      this.choix.set({});
      if (!anneeId) {
        this.classes.set([]);
        return;
      }
      this.api.classes(anneeId).subscribe({
        next: (classes) => this.classes.set(classes),
        error: (e) => this.erreur.set(messageErreur(e)),
      });
    });
  }

  protected choisirClasse(id: string): void {
    this.classeId.set(id);
    this.information.set('');
    this.charger();
  }

  protected definir(enseignementId: string, enseignantId: string | null): void {
    this.choix.update((choix) => ({ ...choix, [enseignementId]: enseignantId }));
  }

  protected enregistrer(): void {
    const classeId = this.classeId();
    if (!classeId) {
      return;
    }
    const choix = this.choix();
    const affectations = this.enseignements().map((e) => ({
      enseignementId: e.id,
      enseignantId: choix[e.id] ?? null,
    }));
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    this.api.affecter(classeId, affectations).subscribe({
      next: (enseignements) => {
        this.appliquer(enseignements);
        this.information.set('Les affectations de la classe ont été enregistrées.');
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected annuler(): void {
    this.appliquer(this.enseignements());
    this.information.set('');
  }

  protected voirCharge(enseignantId: string | null): void {
    this.enseignantObserve = enseignantId;
    this.charge.set(null);
    if (!enseignantId) {
      return;
    }
    this.api.chargeEnseignant(enseignantId).subscribe({
      next: (charge) => this.charge.set(charge),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected nomClasse(): string {
    return this.classes().find((c) => c.id === this.classeId())?.nom ?? '';
  }

  private charger(): void {
    const classeId = this.classeId();
    if (!classeId) {
      return;
    }
    this.api.enseignements(classeId).subscribe({
      next: (enseignements) => this.appliquer(enseignements),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private appliquer(enseignements: Enseignement[]): void {
    this.enseignements.set(enseignements);
    const choix: Record<string, string | null> = {};
    for (const enseignement of enseignements) {
      choix[enseignement.id] = enseignement.enseignantId;
    }
    this.choix.set(choix);
  }
}
