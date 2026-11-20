import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  CategorieTarif,
  Classe,
  Niveau,
  Tarif,
  TypeFrais,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { confirmation } from '@shared/composants/confirmation';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { AnneeRequise } from '@shared/composants/annee-requise';

/** Montant de chaque frais par niveau ou classe et par catégorie d'élève (SF-22). */
@Component({
  selector: 'app-grille-tarifaire',
  templateUrl: './grille-tarifaire.html',
  imports: [
    FormsModule,
    DecimalPipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatExpansionModule,
    PageHeader,
    BoutonFormulaire,
    AnneeRequise,
  ],
})
export class GrilleTarifaire {
  private readonly api = inject(ApiService);
  /** Demande confirmation avant toute suppression. */
  private readonly confirmer = confirmation();
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['cible', 'categorie', 'frais', 'montant', 'actions'];
  protected readonly tarifs = signal<Tarif[]>([]);
  protected readonly types = signal<TypeFrais[]>([]);
  protected readonly niveaux = signal<Niveau[]>([]);
  protected readonly classes = signal<Classe[]>([]);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  /** Total par cible et catégorie, affiché sous le tableau. */
  protected readonly totaux = computed(() => {
    const parCle = new Map<string, number>();
    for (const tarif of this.tarifs()) {
      const cle = `${tarif.niveauCode ?? tarif.classeNom} — ${this.libelleCategorie(tarif.categorie)}`;
      parCle.set(cle, (parCle.get(cle) ?? 0) + tarif.montant);
    }
    return [...parCle.entries()].map(([cle, total]) => ({ cle, total }));
  });

  protected nouveau = this.vide();
  protected duplication = { anneeSourceId: null as string | null, haussePourcentage: 0 };

  constructor() {
    this.api.typesFrais(true).subscribe({
      next: (types) => this.types.set(types),
      error: () => undefined,
    });
    this.api.niveaux().subscribe({
      next: (niveaux) => this.niveaux.set(niveaux),
      error: () => undefined,
    });
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (anneeId) {
        this.charger(anneeId);
      } else {
        this.tarifs.set([]);
      }
    });
  }

  protected libelleCategorie(categorie: CategorieTarif): string {
    return { NOUVEAU: 'Nouveaux', ANCIEN: 'Anciens', TOUS: 'Tous' }[categorie];
  }

  protected cible(tarif: Tarif): string {
    return tarif.classeNom ? `Classe ${tarif.classeNom}` : `Niveau ${tarif.niveauCode}`;
  }

  /** La fenêtre ne se referme qu'une fois le tarif ajouté. */
  protected ajouter(fenetre?: BoutonFormulaire): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId || !this.nouveau.typeFraisId || this.nouveau.montant === null) {
      return;
    }
    const parClasse = this.nouveau.porteeClasse;
    this.executer(
      this.api.ajouterTarif(anneeId, {
        typeFraisId: this.nouveau.typeFraisId,
        niveauId: parClasse ? null : this.nouveau.niveauId,
        classeId: parClasse ? this.nouveau.classeId : null,
        categorie: this.nouveau.categorie,
        montant: this.nouveau.montant,
      }),
      () => {
        this.nouveau = {
          ...this.vide(),
          porteeClasse: parClasse,
          categorie: this.nouveau.categorie,
        };
        fenetre?.fermer();
      },
    );
  }

  protected supprimer(tarif: Tarif): void {
    this.confirmer({
      titre: 'Supprimer le tarif',
      message:
        `Supprimer le tarif « ${tarif.typeFraisLibelle} » ` +
        `(${tarif.classeNom ?? tarif.niveauCode ?? 'toutes classes'}) ?`,
      precision: 'Les échéanciers déjà calculés ne changent pas.',
    }).subscribe(() => this.executer(this.api.supprimerTarif(tarif.id)));
  }

  protected vider(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId) {
      return;
    }
    this.confirmer({
      titre: 'Vider la grille tarifaire',
      message: "Supprimer tous les tarifs de l'année de travail ?",
      precision: 'La grille sera entièrement à ressaisir.',
      action: 'Vider la grille',
    }).subscribe(() => {
      this.executer(this.api.viderGrille(anneeId));
    });
  }

  protected dupliquer(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId || !this.duplication.anneeSourceId) {
      return;
    }
    this.executer(
      this.api.dupliquerGrille(
        anneeId,
        this.duplication.anneeSourceId,
        this.duplication.haussePourcentage,
      ),
      () => this.information.set('Grille reconduite.'),
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
        const anneeId = this.contexte.anneeId();
        if (anneeId) {
          this.charger(anneeId);
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private charger(anneeId: string): void {
    this.api.grilleTarifaire(anneeId).subscribe({
      next: (tarifs) => this.tarifs.set(tarifs),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.api.classes(anneeId).subscribe({
      next: (classes) => this.classes.set(classes),
      error: () => undefined,
    });
  }

  private vide() {
    return {
      typeFraisId: null as string | null,
      porteeClasse: false,
      niveauId: null as string | null,
      classeId: null as string | null,
      categorie: 'TOUS' as CategorieTarif,
      montant: null as number | null,
    };
  }
}
