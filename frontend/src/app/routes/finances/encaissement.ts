import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Observable } from 'rxjs';

import { AnneeContexte } from '@core/annee-contexte';
import {
  ApiService,
  Classe,
  FicheFinanciere,
  FraisEleve,
  Inscription,
  ModePaiement,
  Paiement,
  TypeFrais,
  TypeRemise,
  messageErreur,
} from '@core/api.service';
import { AuthService } from '@core/authentication';
import { MtxSelectModule } from '@ng-matero/extensions/select';

import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { aujourdhui, enIso } from '@shared/composants/dates';

/** Fiche financière de l'élève et encaissement (SF-28 à SF-31). */
@Component({
  selector: 'app-encaissement',
  templateUrl: './encaissement.html',
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
    MatExpansionModule,
    MatTooltipModule,
    MatDatepickerModule,
    MtxSelectModule,
    MatTabsModule,
    PageHeader,
    AnneeRequise,
    BoutonFormulaire,
  ],
})
export class Encaissement {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly peutEncaisser = this.auth.aUnRole('INTENDANT', 'ECONOME');
  protected readonly peutParametrer = this.auth.aUnRole('INTENDANT');
  protected readonly peutRemiser = this.auth.aUnRole('INTENDANT', 'DIRECTEUR');

  protected readonly colonnesEcheances = [
    'tranche',
    'frais',
    'date',
    'du',
    'paye',
    'reste',
    'statut',
  ];
  protected readonly colonnesFrais = ['frais', 'brut', 'remise', 'net', 'actions'];
  protected readonly colonnesPaiements = [
    'recu',
    'date',
    'montant',
    'mode',
    'payeur',
    'statut',
    'actions',
  ];

  protected readonly modes: { code: ModePaiement; libelle: string }[] = [
    { code: 'ESPECES', libelle: 'Espèces' },
    { code: 'MOBILE_MONEY', libelle: 'Mobile Money' },
    { code: 'VIREMENT', libelle: 'Virement' },
    { code: 'CHEQUE', libelle: 'Chèque' },
  ];

  protected readonly classes = signal<Classe[]>([]);
  protected readonly inscriptions = signal<Inscription[]>([]);
  /** Libellé « NOM Prénom (matricule) » : c'est sur lui que porte la recherche. */
  protected readonly elevesAffiches = computed(() =>
    this.inscriptions().map((i) => ({
      id: i.id,
      libelle: `${i.eleve.nom} ${i.eleve.prenom} (${i.eleve.matricule})`,
    })),
  );
  protected readonly classeId = signal<string | null>(null);
  protected readonly inscriptionId = signal<string | null>(null);
  protected readonly fiche = signal<FicheFinanciere | null>(null);
  protected readonly typesFrais = signal<TypeFrais[]>([]);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  protected readonly fraisFacultatifs = computed(() => {
    const posees = new Set(this.fiche()?.frais.map((f) => f.typeFraisId) ?? []);
    return this.typesFrais().filter((t) => !posees.has(t.id));
  });

  protected paiement = this.vide();
  protected fraisAAjouter: string | null = null;
  protected remise: { fraisId: string; type: TypeRemise; valeur: number; motif: string } | null =
    null;
  protected annulation: { paiementId: string; motif: string } | null = null;

  constructor() {
    this.api.typesFrais(true).subscribe({
      next: (types) => this.typesFrais.set(types),
      error: () => undefined,
    });
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.classeId.set(null);
      this.inscriptionId.set(null);
      this.fiche.set(null);
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
    this.inscriptionId.set(null);
    this.fiche.set(null);
    this.api.toutesLesInscriptions(id).subscribe({
      next: (inscriptions) => this.inscriptions.set(inscriptions),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected choisirEleve(id: string): void {
    this.inscriptionId.set(id);
    this.remise = null;
    this.annulation = null;
    this.chargerFiche();
  }

  /** Ton de la pastille d'une échéance : vert soldé, ambre partiel, rouge en retard. */
  protected tonStatut(statut: string): string {
    switch (statut) {
      case 'PAYEE':
        return 'etat-ok';
      case 'EN_RETARD':
        return 'etat-alerte';
      case 'PARTIEL':
        return 'etat-attention';
      default:
        return 'etat-info';
    }
  }

  protected libelleStatut(statut: string): string {
    return (
      (
        {
          A_VENIR: 'À venir',
          PARTIEL: 'Partiellement payée',
          PAYEE: 'Payée',
          EN_RETARD: 'En retard',
        } as Record<string, string>
      )[statut] ?? statut
    );
  }

  protected libelleMode(mode: ModePaiement): string {
    return this.modes.find((m) => m.code === mode)?.libelle ?? mode;
  }

  // ---------- Actions ----------

  protected genererFrais(): void {
    const id = this.inscriptionId();
    if (!id) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    this.api.genererFrais(id).subscribe({
      next: (anomalies) => {
        this.information.set(
          anomalies.length === 0
            ? 'Frais et échéancier à jour.'
            : 'Frais générés, avec des points à corriger : ' + anomalies.join(' ; '),
        );
        this.enCours.set(false);
        this.chargerFiche();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected ajouterFrais(): void {
    const id = this.inscriptionId();
    if (id && this.fraisAAjouter) {
      this.executer(this.api.ajouterFraisFacultatif(id, this.fraisAAjouter), () => {
        this.fraisAAjouter = null;
      });
    }
  }

  protected ouvrirRemise(frais: FraisEleve): void {
    this.remise = {
      fraisId: frais.id,
      type: frais.remiseType ?? 'POURCENTAGE',
      valeur: frais.remiseValeur ?? 0,
      motif: frais.motifRemise ?? '',
    };
  }

  protected enregistrerRemise(): void {
    const r = this.remise;
    if (!r) {
      return;
    }
    this.executer(
      this.api.appliquerRemise(r.fraisId, { type: r.type, valeur: r.valeur, motif: r.motif }),
      () => (this.remise = null),
    );
  }

  protected retirerRemise(frais: FraisEleve): void {
    this.executer(
      this.api.appliquerRemise(frais.id, { type: null, valeur: null, motif: null }),
      () => (this.remise = null),
    );
  }

  protected supprimerFrais(frais: FraisEleve): void {
    this.executer(this.api.supprimerFrais(frais.id));
  }

  /** La fenêtre ne se referme qu'une fois le règlement enregistré. */
  protected encaisser(fenetre?: BoutonFormulaire): void {
    const id = this.inscriptionId();
    if (!id || !this.paiement.montant) {
      return;
    }
    this.executer(
      this.api.encaisser(id, {
        datePaiement: enIso(this.paiement.datePaiement) ?? aujourdhui(),
        montant: this.paiement.montant,
        mode: this.paiement.mode,
        reference: this.paiement.reference || null,
        payeur: this.paiement.payeur || null,
        typeFraisId: this.paiement.typeFraisId,
      }),
      () => {
        this.information.set('Règlement enregistré.');
        this.paiement = this.vide();
        fenetre?.fermer();
      },
    );
  }

  protected ouvrirAnnulation(paiement: Paiement): void {
    this.annulation = { paiementId: paiement.id, motif: '' };
  }

  protected annuler(): void {
    const a = this.annulation;
    if (!a || !a.motif.trim()) {
      return;
    }
    this.executer(this.api.annulerPaiement(a.paiementId, a.motif), () => {
      this.annulation = null;
      this.information.set('Paiement annulé.');
    });
  }

  protected ouvrirRecu(paiement: Paiement, duplicata: boolean): void {
    window.open(`/recu/${paiement.id}${duplicata ? '?duplicata=true' : ''}`, '_blank');
  }

  // ---------- Outils ----------

  private executer(action: Observable<unknown>, apres?: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    action.subscribe({
      next: () => {
        apres?.();
        this.enCours.set(false);
        this.chargerFiche();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private chargerFiche(): void {
    const id = this.inscriptionId();
    if (!id) {
      return;
    }
    this.api.ficheFinanciere(id).subscribe({
      next: (fiche) => this.fiche.set(fiche),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private vide() {
    return {
      datePaiement: new Date() as Date | null,
      montant: null as number | null,
      mode: 'ESPECES' as ModePaiement,
      reference: '',
      payeur: '',
      typeFraisId: null as string | null,
    };
  }
}
