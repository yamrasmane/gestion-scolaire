import { DatePipe } from '@angular/common';
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
  CompositionEffective,
  Cycle,
  Trimestre,
  messageErreur,
} from '@core/api.service';
import { AuthService } from '@core/authentication';
import { DIRECTION } from '@core/roles';
import { PageHeader } from '@shared/components/page-header/page-header';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { enIso } from '@shared/composants/dates';
import { AnneeRequise } from '@shared/composants/annee-requise';

type ChoixTrimestre = 'ANNEE' | 'AVEC' | 'SANS';

@Component({
  selector: 'app-trimestres',
  templateUrl: './trimestres.html',
  imports: [
    FormsModule,
    DatePipe,
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
    BoutonFormulaire,
    AnneeRequise,
  ],
})
export class Trimestres {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);
  protected readonly peutModifier = inject(AuthService).aUnRole(...DIRECTION);

  protected readonly cycles: { code: Cycle; libelle: string }[] = [
    { code: 'PREMIER', libelle: 'Premier cycle' },
    { code: 'SECOND', libelle: 'Second cycle' },
  ];
  protected readonly colonnes = this.peutModifier
    ? ['cycle', 'composition', 'actions']
    : ['cycle', 'composition'];
  protected readonly trimestres = signal<Trimestre[]>([]);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected nouveau = { numero: 1, dateDebut: null as Date | null, dateFin: null as Date | null };
  /** Saisies en cours, par trimestre. */
  protected choix: Record<string, { mode: ChoixTrimestre; poids: number }> = {};
  protected cycleEnEdition: {
    trimestreId: string;
    cycle: Cycle;
    avec: boolean;
    poids: number;
  } | null = null;

  constructor() {
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (anneeId) {
        this.charger(anneeId);
      } else {
        this.trimestres.set([]);
      }
    });
  }

  /** Réglage appliqué à un cycle (les lignes du tableau Material ne sont pas typées). */
  protected compositionDe(t: Trimestre, cycle: string): CompositionEffective {
    return t.compositionParCycle[cycle as Cycle];
  }

  protected decrire(c: CompositionEffective): string {
    const origine = {
      CYCLE: 'réglage du cycle',
      TRIMESTRE: 'réglage du trimestre',
      ANNEE: "réglage de l'année",
    };
    return (
      (c.avecComposition ? `Avec composition (poids ${c.poidsComposition})` : 'Sans composition') +
      ` — ${origine[c.source]}`
    );
  }

  /** La fenêtre ne se referme qu'une fois le trimestre créé. */
  protected creer(fenetre?: BoutonFormulaire): void {
    const anneeId = this.contexte.anneeId();
    if (anneeId && this.nouveau.dateDebut && this.nouveau.dateFin) {
      this.executer(
        this.api.creerTrimestre(anneeId, {
          numero: this.nouveau.numero,
          dateDebut: enIso(this.nouveau.dateDebut) ?? '',
          dateFin: enIso(this.nouveau.dateFin) ?? '',
        }),
        fenetre,
      );
    }
  }

  protected enregistrerReglage(t: Trimestre): void {
    const c = this.choix[t.id];
    this.executer(
      this.api.reglerComposition(t.id, {
        avecComposition: c.mode === 'ANNEE' ? null : c.mode === 'AVEC',
        poidsComposition: c.mode === 'AVEC' ? c.poids : null,
      }),
    );
  }

  protected editerCycle(t: Trimestre, cycle: Cycle): void {
    const actuel = t.compositionParCycle[cycle];
    this.cycleEnEdition = {
      trimestreId: t.id,
      cycle,
      avec: actuel.avecComposition,
      poids: actuel.poidsComposition,
    };
  }

  protected enregistrerCycle(): void {
    const e = this.cycleEnEdition;
    if (e) {
      this.executer(
        this.api.reglerCompositionCycle(e.trimestreId, e.cycle, {
          avecComposition: e.avec,
          poidsComposition: e.poids,
        }),
      );
    }
  }

  protected supprimerCycle(t: Trimestre, cycle: Cycle): void {
    this.executer(this.api.supprimerReglageCycle(t.id, cycle));
  }

  protected basculerCloture(t: Trimestre): void {
    this.executer(t.cloture ? this.api.rouvrirTrimestre(t.id) : this.api.cloturerTrimestre(t.id));
  }

  private executer(action: Observable<unknown>, fenetre?: BoutonFormulaire): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: () => {
        this.enCours.set(false);
        this.cycleEnEdition = null;
        fenetre?.fermer();
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
    this.api.trimestres(anneeId).subscribe({
      next: (trimestres) => {
        this.choix = {};
        for (const t of trimestres) {
          this.choix[t.id] = {
            mode: t.avecComposition === null ? 'ANNEE' : t.avecComposition ? 'AVEC' : 'SANS',
            poids: t.poidsComposition ?? this.contexte.annee()?.poidsCompositionDefaut ?? 2,
          };
        }
        const numeros = trimestres.map((t) => t.numero);
        this.nouveau = {
          numero: [1, 2, 3].find((n) => !numeros.includes(n)) ?? 1,
          dateDebut: null,
          dateFin: null,
        };
        this.trimestres.set(trimestres);
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
