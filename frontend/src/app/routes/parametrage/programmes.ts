import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MtxSelectModule } from '@ng-matero/extensions/select';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  ApplicationProgramme,
  LigneProgramme,
  Matiere,
  Niveau,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { AnneeRequise } from '@shared/composants/annee-requise';

/**
 * Programme d'un niveau : les matières enseignées et leur coefficient
 * pour un type de classe (SF-09). Toute classe créée ensuite dans ce
 * niveau reçoit automatiquement ces matières ; le bouton « Appliquer »
 * complète en plus les classes qui existent déjà.
 */
@Component({
  selector: 'app-programmes',
  templateUrl: './programmes.html',
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MtxSelectModule,
    MatTabsModule,
    PageHeader,
    BoutonFormulaire,
    AnneeRequise,
  ],
})
export class Programmes {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['matiere', 'coefficient', 'actions'];
  protected readonly niveaux = signal<Niveau[]>([]);
  protected readonly matieres = signal<Matiere[]>([]);
  protected readonly lignes = signal<LigneProgramme[]>([]);
  protected readonly niveauId = signal<string | null>(null);
  protected readonly erreur = signal('');
  protected readonly compteRendu = signal<ApplicationProgramme | null>(null);
  protected readonly enCours = signal(false);
  protected readonly ligneModifiee = signal<string | null>(null);

  /** Total des coefficients : c'est le dénominateur de la moyenne générale. */
  protected readonly totalCoefficients = computed(() =>
    this.lignes().reduce((total, ligne) => total + Number(ligne.coefficient), 0),
  );

  /** Matières absentes du programme affiché : seules celles-ci sont proposées. */
  protected readonly matieresDisponibles = computed(() => {
    const prises = new Set(this.lignes().map((ligne) => ligne.matiereId));
    return this.matieres().filter((matiere) => !prises.has(matiere.id));
  });

  protected nouvelleLigne = this.vide();
  protected coefficientSaisi = 1;
  protected alignerCoefficients = false;

  constructor() {
    this.api.niveaux().subscribe({
      next: (niveaux) => this.niveaux.set(niveaux),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.api.matieres().subscribe({
      next: (matieres) => this.matieres.set(matieres),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected choisirNiveau(id: string): void {
    this.niveauId.set(id);
    this.compteRendu.set(null);
    this.ligneModifiee.set(null);
    this.charger();
  }

  /** La fenêtre ne se referme qu'une fois la ligne ajoutée. */
  protected ajouter(fenetre?: BoutonFormulaire): void {
    const niveauId = this.niveauId();
    if (!niveauId || !this.nouvelleLigne.matiereId || !this.nouvelleLigne.coefficient) {
      return;
    }
    this.executer(
      this.api.ajouterAuProgramme(niveauId, {
        matiereId: this.nouvelleLigne.matiereId,
        coefficient: this.nouvelleLigne.coefficient,
      }),
      () => {
        this.nouvelleLigne = this.vide();
        fenetre?.fermer();
        this.charger();
      },
    );
  }

  protected modifier(ligne: LigneProgramme): void {
    this.ligneModifiee.set(ligne.id);
    this.coefficientSaisi = Number(ligne.coefficient);
  }

  protected enregistrer(ligne: LigneProgramme): void {
    if (!this.coefficientSaisi || this.coefficientSaisi <= 0) {
      return;
    }
    this.executer(
      this.api.modifierLigneProgramme(ligne.id, {
        matiereId: ligne.matiereId,
        coefficient: this.coefficientSaisi,
      }),
      () => {
        this.ligneModifiee.set(null);
        this.charger();
      },
    );
  }

  protected retirer(ligne: LigneProgramme): void {
    this.executer(this.api.retirerDuProgramme(ligne.id), () => this.charger());
  }

  /** Complète les classes déjà créées dans ce niveau pour l'année de travail. */
  protected appliquer(): void {
    const niveauId = this.niveauId();
    const anneeId = this.contexte.anneeId();
    if (!niveauId || !anneeId) {
      return;
    }
    this.compteRendu.set(null);
    this.executer(
      this.api.appliquerProgramme(niveauId, anneeId, this.alignerCoefficients),
      (resultat) => this.compteRendu.set(resultat as ApplicationProgramme),
    );
  }

  protected nomNiveau(): string {
    return this.niveaux().find((n) => n.id === this.niveauId())?.libelle ?? '';
  }

  private charger(): void {
    const niveauId = this.niveauId();
    if (!niveauId) {
      return;
    }
    this.api.programme(niveauId).subscribe({
      next: (lignes) => this.lignes.set(lignes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private executer<T>(action: Observable<T>, apres: (resultat: T) => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: (resultat) => {
        apres(resultat);
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private vide() {
    return { matiereId: null as string | null, coefficient: 1 as number | null };
  }
}
