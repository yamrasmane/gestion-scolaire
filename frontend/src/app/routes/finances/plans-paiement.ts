import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  Classe,
  ModeRepartition,
  Niveau,
  PlanPaiement,
  messageErreur,
} from '@core/api.service';
import { PageHeader } from '@shared/components/page-header/page-header';
import { confirmation } from '@shared/composants/confirmation';
import { depuisIso, enIso } from '@shared/composants/dates';
import { AnneeRequise } from '@shared/composants/annee-requise';

interface TrancheSaisie {
  numero: number;
  libelle: string;
  dateEcheance: Date | null;
  pourcentage: number | null;
  montant: number | null;
}

/** Plans de paiement par tranches configurables (SF-26). */
@Component({
  selector: 'app-plans-paiement',
  templateUrl: './plans-paiement.html',
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
    MatCheckboxModule,
    MatDatepickerModule,
    PageHeader,
    AnneeRequise,
  ],
})
export class PlansPaiement {
  private readonly api = inject(ApiService);
  /** Demande confirmation avant toute suppression. */
  private readonly confirmer = confirmation();
  protected readonly contexte = inject(AnneeContexte);

  protected readonly colonnes = ['numero', 'libelle', 'echeance', 'part'];
  protected readonly plans = signal<PlanPaiement[]>([]);
  protected readonly niveaux = signal<Niveau[]>([]);
  protected readonly classes = signal<Classe[]>([]);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);
  /** Plan en cours de modification ; `null` quand on en crée un nouveau. */
  protected readonly enEdition = signal<string | null>(null);
  protected readonly formulaireOuvert = signal(false);

  protected saisie = this.vide();

  constructor() {
    this.api.niveaux().subscribe({
      next: (niveaux) => this.niveaux.set(niveaux),
      error: () => undefined,
    });
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (anneeId) {
        this.charger(anneeId);
      } else {
        this.plans.set([]);
      }
    });
  }

  protected part(
    plan: PlanPaiement,
    tranche: { pourcentage: number | null; montant: number | null },
  ): string {
    return plan.modeRepartition === 'POURCENTAGE'
      ? `${tranche.pourcentage} %`
      : `${tranche.montant} FCFA`;
  }

  protected portee(plan: PlanPaiement): string {
    const cible = plan.classeNom
      ? `classe ${plan.classeNom}`
      : plan.niveauCode
        ? `niveau ${plan.niveauCode}`
        : 'toute l’année';
    const categorie =
      plan.categorie === 'NOUVEAU'
        ? ', nouveaux élèves'
        : plan.categorie === 'ANCIEN'
          ? ', anciens élèves'
          : '';
    return cible + categorie;
  }

  protected nouveauPlan(): void {
    this.saisie = this.vide();
    this.enEdition.set(null);
    this.formulaireOuvert.set(true);
  }

  protected editer(plan: PlanPaiement): void {
    this.saisie = {
      libelle: plan.libelle,
      porteeClasse: plan.classeId !== null,
      toutePortee: plan.classeId === null && plan.niveauId === null,
      niveauId: plan.niveauId,
      classeId: plan.classeId,
      categorie: plan.categorie,
      modeRepartition: plan.modeRepartition,
      parDefaut: plan.parDefaut,
      montantMinInscription: plan.montantMinInscription,
      tranches: plan.tranches.map((t) => ({
        numero: t.numero,
        libelle: t.libelle,
        dateEcheance: depuisIso(t.dateEcheance),
        pourcentage: t.pourcentage,
        montant: t.montant,
      })),
    };
    this.enEdition.set(plan.id);
    this.formulaireOuvert.set(true);
  }

  protected ajouterTranche(): void {
    this.saisie.tranches.push({
      numero: this.saisie.tranches.length + 1,
      libelle: `Tranche ${this.saisie.tranches.length + 1}`,
      dateEcheance: null,
      pourcentage: null,
      montant: null,
    });
  }

  protected retirerTranche(index: number): void {
    this.saisie.tranches.splice(index, 1);
    this.saisie.tranches.forEach((t, i) => (t.numero = i + 1));
  }

  protected enregistrer(): void {
    const anneeId = this.contexte.anneeId();
    if (!anneeId) {
      return;
    }
    const corps = {
      libelle: this.saisie.libelle,
      niveauId: this.saisie.toutePortee || this.saisie.porteeClasse ? null : this.saisie.niveauId,
      classeId: this.saisie.toutePortee || !this.saisie.porteeClasse ? null : this.saisie.classeId,
      categorie: this.saisie.categorie,
      modeRepartition: this.saisie.modeRepartition,
      parDefaut: this.saisie.parDefaut,
      montantMinInscription: this.saisie.montantMinInscription,
      // L'API attend « 2026-11-30 » là où le calendrier rend une Date.
      tranches: this.saisie.tranches.map((t) => ({
        ...t,
        dateEcheance: enIso(t.dateEcheance) ?? '',
      })),
    };
    const planId = this.enEdition();
    this.executer(
      planId ? this.api.modifierPlan(planId, corps) : this.api.creerPlan(anneeId, corps),
      () => this.formulaireOuvert.set(false),
    );
  }

  protected supprimer(plan: PlanPaiement): void {
    this.confirmer({
      titre: 'Supprimer le plan de paiement',
      message: `Supprimer « ${plan.libelle} » ?`,
      precision: 'Les échéanciers déjà établis avec ce plan ne changent pas.',
    }).subscribe(() => this.executer(this.api.supprimerPlan(plan.id)));
  }

  private executer(action: Observable<unknown>, apres?: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
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
    this.api.plansPaiement(anneeId).subscribe({
      next: (plans) => this.plans.set(plans),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.api.classes(anneeId).subscribe({
      next: (classes) => this.classes.set(classes),
      error: () => undefined,
    });
  }

  private vide() {
    return {
      libelle: '',
      porteeClasse: false,
      toutePortee: true,
      niveauId: null as string | null,
      classeId: null as string | null,
      categorie: null as 'NOUVEAU' | 'ANCIEN' | null,
      modeRepartition: 'POURCENTAGE' as ModeRepartition,
      parDefaut: false,
      montantMinInscription: null as number | null,
      tranches: [] as TrancheSaisie[],
    };
  }
}
