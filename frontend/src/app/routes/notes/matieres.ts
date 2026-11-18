import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MtxSelectModule } from '@ng-matero/extensions/select';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  Classe,
  Enseignement,
  Matiere,
  Personnel,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';

/** Catalogue des matières et affectation par classe (SF-09, SF-10). */
@Component({
  selector: 'app-matieres',
  templateUrl: './matieres.html',
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MtxSelectModule,
    PageHeader,
  ],
})
export class Matieres {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['matiere', 'enseignant', 'coefficient', 'actions'];
  protected readonly matieres = signal<Matiere[]>([]);
  protected readonly classes = signal<Classe[]>([]);
  protected readonly enseignants = signal<Personnel[]>([]);
  protected readonly enseignements = signal<Enseignement[]>([]);
  protected readonly classeId = signal<string | null>(null);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected readonly colonnesCatalogue = ['code', 'libelle', 'actions'];
  protected readonly matiereModifiee = signal<string | null>(null);
  /** « NOM Prénom » : c'est sur ce libellé que porte la recherche. */
  protected readonly professeurs = computed(() =>
    this.enseignants().map((p) => ({ id: p.id, libelle: `${p.nom} ${p.prenom}` })),
  );
  protected nouvelleMatiere = { code: '', libelle: '' };
  protected matiereSaisie = { code: '', libelle: '' };
  protected affectation = this.videAffectation();

  constructor() {
    this.chargerMatieres();
    this.api.tousLesPersonnels().subscribe({
      next: (personnel) => this.enseignants.set(personnel.filter((p) => p.actif)),
      error: () => undefined,
    });
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.classeId.set(null);
      this.enseignements.set([]);
      if (anneeId) {
        this.api.classes(anneeId).subscribe({
          next: (classes) => this.classes.set(classes),
          error: (e) => this.erreur.set(messageErreur(e)),
        });
      } else {
        this.classes.set([]);
      }
    });
  }

  protected choisirClasse(id: string): void {
    this.classeId.set(id);
    this.chargerEnseignements();
  }

  protected creerMatiere(): void {
    if (!this.nouvelleMatiere.code.trim() || !this.nouvelleMatiere.libelle.trim()) {
      return;
    }
    this.executer(this.api.creerMatiere(this.nouvelleMatiere), () => {
      this.nouvelleMatiere = { code: '', libelle: '' };
      this.chargerMatieres();
    });
  }

  protected editerMatiere(matiere: Matiere): void {
    this.matiereModifiee.set(matiere.id);
    this.matiereSaisie = { code: matiere.code, libelle: matiere.libelle };
  }

  protected enregistrerMatiere(matiere: Matiere): void {
    if (!this.matiereSaisie.code.trim() || !this.matiereSaisie.libelle.trim()) {
      return;
    }
    this.executer(this.api.modifierMatiere(matiere.id, this.matiereSaisie), () => {
      this.matiereModifiee.set(null);
      this.chargerMatieres();
    });
  }

  protected supprimerMatiere(matiere: Matiere): void {
    this.executer(this.api.supprimerMatiere(matiere.id), () => this.chargerMatieres());
  }

  protected affecter(): void {
    const classeId = this.classeId();
    if (!classeId || !this.affectation.matiereId || !this.affectation.coefficient) {
      return;
    }
    this.executer(
      this.api.ajouterEnseignement(classeId, {
        matiereId: this.affectation.matiereId,
        enseignantId: this.affectation.enseignantId,
        coefficient: this.affectation.coefficient,
      }),
      () => {
        this.affectation = this.videAffectation();
        this.chargerEnseignements();
      },
    );
  }

  protected retirer(enseignement: Enseignement): void {
    this.executer(this.api.supprimerEnseignement(enseignement.id), () =>
      this.chargerEnseignements(),
    );
  }

  private executer(action: Observable<unknown>, apres: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: () => {
        apres();
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private chargerMatieres(): void {
    this.api.matieres().subscribe({
      next: (matieres) => this.matieres.set(matieres),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private chargerEnseignements(): void {
    const classeId = this.classeId();
    if (!classeId) {
      return;
    }
    this.api.enseignements(classeId).subscribe({
      next: (enseignements) => this.enseignements.set(enseignements),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private videAffectation() {
    return {
      matiereId: null as string | null,
      enseignantId: null as string | null,
      coefficient: 1 as number | null,
    };
  }
}
