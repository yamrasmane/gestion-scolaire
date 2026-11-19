import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AnneeContexte } from '@core/annee-contexte';
import { ApiService, Classe, Impaye, Recouvrement, messageErreur } from '@core/api.service';
import { AuthService } from '@core/authentication';
import { FINANCES } from '@core/roles';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AvisRoles } from '@shared/composants/avis-roles';
import { aujourdhui } from '@shared/composants/dates';

/** Une alerte du tableau de bord : une pastille et une phrase. */
interface Alerte {
  pastille: string;
  ton: string;
  texte: string;
  lien?: string;
}

/**
 * Tableau de bord d'accueil : quatre chiffres clés, les échéances en
 * souffrance, les alertes du jour et un récapitulatif des effectifs.
 * Chaque bloc ne s'affiche que si la personne a le droit de voir ces données.
 */
@Component({
  selector: 'app-accueil',
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
  imports: [
    RouterLink,
    DecimalPipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressBarModule,
    PageHeader,
    AvisRoles,
  ],
})
export class Accueil {
  protected readonly auth = inject(AuthService);
  protected readonly contexte = inject(AnneeContexte);
  private readonly api = inject(ApiService);

  protected readonly colonnes = ['eleve', 'classe', 'frais', 'echeance', 'reste'];

  protected readonly classes = signal<Classe[]>([]);
  protected readonly recouvrement = signal<Recouvrement | null>(null);
  protected readonly impayes = signal<Impaye[]>([]);
  protected readonly nombreImpayes = signal(0);
  protected readonly encaisseDuJour = signal<number | null>(null);
  protected readonly personnelActif = signal<number | null>(null);
  protected readonly erreur = signal('');

  /** Les chiffres financiers ne concernent pas tout le monde. */
  protected readonly voitLesFinances = computed(() => this.auth.aUnRole(...FINANCES));

  protected readonly effectif = computed(() =>
    this.classes().reduce((total, classe) => total + classe.effectif, 0),
  );

  protected readonly placesRestantes = computed(() =>
    this.classes().reduce((total, classe) => total + (classe.effectifMax - classe.effectif), 0),
  );

  /** Part des sommes attendues déjà encaissées, en pourcentage entier. */
  protected readonly tauxRecouvrement = computed(() => {
    const r = this.recouvrement();
    if (!r || r.attendu === 0) {
      return null;
    }
    return Math.round((r.encaisse / r.attendu) * 100);
  });

  protected readonly classesPleines = computed(() =>
    this.classes().filter((c) => c.effectif >= c.effectifMax),
  );

  /** Alertes construites à partir des seules données déjà chargées. */
  protected readonly alertes = computed<Alerte[]>(() => {
    const alertes: Alerte[] = [];
    const r = this.recouvrement();
    if (r && r.elevesEnRetard > 0) {
      alertes.push({
        pastille: `${r.elevesEnRetard}`,
        ton: 'etat-alerte',
        texte: `élève(s) en retard de paiement`,
        lien: '/finances/impayes',
      });
    }
    if (r && r.echuNonPaye > 0) {
      alertes.push({
        pastille: 'Échu',
        ton: 'etat-attention',
        texte: `${r.echuNonPaye.toLocaleString('fr-FR')} FCFA échus non réglés`,
        lien: '/finances/impayes',
      });
    }
    for (const classe of this.classesPleines()) {
      alertes.push({
        pastille: 'Pleine',
        ton: 'etat-attention',
        texte: `${classe.nom} a atteint son effectif maximum (${classe.effectifMax})`,
        lien: '/parametrage/classes',
      });
    }
    if (this.classes().length === 0 && this.contexte.anneeId()) {
      alertes.push({
        pastille: 'Vide',
        ton: 'etat-info',
        texte: "Aucune classe n'est ouverte pour cette année",
        lien: '/parametrage/classes',
      });
    }
    return alertes;
  });

  constructor() {
    this.api.personnel({ taille: 1, actif: true }).subscribe({
      next: (page) => this.personnelActif.set(page.total),
      error: () => undefined,
    });
    // Les données suivent l'année de travail choisie dans la barre du haut.
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (anneeId) {
        this.charger(anneeId);
      }
    });
  }

  private charger(anneeId: string): void {
    this.api.classes(anneeId).subscribe({
      next: (classes) => this.classes.set(classes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    if (!this.voitLesFinances()) {
      return;
    }
    this.api.recouvrement(anneeId).subscribe({
      next: (recouvrement) => this.recouvrement.set(recouvrement),
      error: () => undefined,
    });
    this.api.impayes(anneeId, { taille: 5 }).subscribe({
      next: (page) => {
        this.impayes.set(page.contenu);
        this.nombreImpayes.set(page.total);
      },
      error: () => undefined,
    });
    this.api.journalCaisse(aujourdhui(), { taille: 1 }).subscribe({
      next: (journal) => this.encaisseDuJour.set(journal.total),
      error: () => undefined,
    });
  }
}
