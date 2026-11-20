import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  Appreciation,
  PorteeAppreciation,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { confirmation } from '@shared/composants/confirmation';

/**
 * Appréciations du bulletin, réglées par intervalles de moyenne (SF-17).
 *
 * <p>Deux barèmes : l'un porte sur les moyennes de matière, ligne par ligne du
 * bulletin, l'autre sur la moyenne générale, où il tient la mention. Les
 * intervalles se lisent « de la borne basse incluse à la borne haute exclue »,
 * 20 étant inclus dans le dernier. Le bulletin les applique de lui-même : il
 * n'y a rien à ressaisir après un changement.
 *
 * <p>Le barème appartient à une année scolaire. Une année nouvelle reçoit le
 * barème de la précédente, qu'il suffit ensuite de retoucher.
 */
@Component({
  selector: 'app-appreciations',
  templateUrl: './appreciations.html',
  imports: [
    NgTemplateOutlet,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatTabsModule,
    PageHeader,
    AnneeRequise,
    BoutonFormulaire,
  ],
})
export class Appreciations {
  private readonly api = inject(ApiService);
  private readonly confirmer = confirmation();
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['intervalle', 'libelle', 'actions'];
  protected readonly matiere = signal<Appreciation[]>([]);
  protected readonly generale = signal<Appreciation[]>([]);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);
  protected readonly ligneModifiee = signal<string | null>(null);

  /** Intervalles manquants : sans eux, une moyenne resterait sans appréciation. */
  protected readonly trousMatiere = computed(() => this.trous(this.matiere()));
  protected readonly trousGenerale = computed(() => this.trous(this.generale()));

  protected nouveau = this.vide();
  protected modification = this.vide();

  constructor() {
    effect(() => {
      this.contexte.anneeId();
      this.ligneModifiee.set(null);
      this.charger();
    });
  }

  protected intervalles(portee: PorteeAppreciation) {
    return portee === 'MATIERE' ? this.matiere() : this.generale();
  }

  protected trousDe(portee: PorteeAppreciation) {
    return portee === 'MATIERE' ? this.trousMatiere() : this.trousGenerale();
  }

  /** La fenêtre ne se referme qu'une fois l'intervalle accepté par le serveur. */
  protected ajouter(portee: PorteeAppreciation, fenetre?: BoutonFormulaire): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId || !this.complet(this.nouveau)) {
      return;
    }
    this.executer(
      this.api.ajouterAppreciation(anneeId, portee, {
        borneMin: Number(this.nouveau.borneMin),
        borneMax: Number(this.nouveau.borneMax),
        libelle: this.nouveau.libelle,
      }),
      () => {
        this.nouveau = this.vide();
        fenetre?.fermer();
        this.charger();
      },
    );
  }

  protected modifier(intervalle: Appreciation): void {
    this.ligneModifiee.set(intervalle.id);
    this.modification = {
      borneMin: Number(intervalle.borneMin),
      borneMax: Number(intervalle.borneMax),
      libelle: intervalle.libelle,
    };
  }

  protected enregistrer(intervalle: Appreciation): void {
    if (!this.complet(this.modification)) {
      return;
    }
    this.executer(
      this.api.modifierAppreciation(intervalle.id, {
        borneMin: Number(this.modification.borneMin),
        borneMax: Number(this.modification.borneMax),
        libelle: this.modification.libelle,
      }),
      () => {
        this.ligneModifiee.set(null);
        this.charger();
      },
    );
  }

  protected supprimer(intervalle: Appreciation): void {
    this.confirmer({
      titre: "Supprimer l'intervalle",
      message: `Supprimer « ${intervalle.libelle} » (${intervalle.borneMin} à ${intervalle.borneMax}) ?`,
      precision:
        'Les moyennes situées dans cet intervalle paraîtront sans appréciation au bulletin, ' +
        "tant qu'un autre intervalle ne les couvre pas.",
      action: 'Supprimer',
    }).subscribe(() =>
      this.executer(this.api.supprimerAppreciation(intervalle.id), () => this.charger()),
    );
  }

  private charger(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId) {
      this.matiere.set([]);
      this.generale.set([]);
      return;
    }
    this.api.appreciations(anneeId, 'MATIERE').subscribe({
      next: (intervalles) => this.matiere.set(intervalles),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.api.appreciations(anneeId, 'GENERALE').subscribe({
      next: (intervalles) => this.generale.set(intervalles),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  /**
   * Plages de 0 à 20 qu'aucun intervalle ne couvre. Un barème peut en garder
   * volontairement — une moyenne générale moyenne n'appelle pas de mention —
   * mais les voir évite de les laisser par oubli.
   */
  private trous(intervalles: Appreciation[]): string[] {
    const manques: string[] = [];
    let borne = 0;
    for (const intervalle of [...intervalles].sort((a, b) => a.borneMin - b.borneMin)) {
      if (Number(intervalle.borneMin) > borne) {
        manques.push(`${borne} à ${intervalle.borneMin}`);
      }
      borne = Math.max(borne, Number(intervalle.borneMax));
    }
    if (borne < 20) {
      manques.push(`${borne} à 20`);
    }
    return manques;
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

  private complet(saisie: { borneMin: number | null; borneMax: number | null; libelle: string }) {
    return (
      saisie.borneMin !== null &&
      saisie.borneMax !== null &&
      Number(saisie.borneMax) > Number(saisie.borneMin) &&
      saisie.libelle.trim().length > 0
    );
  }

  private vide() {
    return { borneMin: 0 as number | null, borneMax: 0 as number | null, libelle: '' };
  }
}
