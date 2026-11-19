import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule } from '@angular/material/sort';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MtxSelectModule } from '@ng-matero/extensions/select';

import { AnneeContexte } from '@core/annee-contexte';
import { ApiService, Classe, Inscription, messageErreur } from '@core/api.service';
import { AuthService } from '@core/authentication';
import { SCOLARITE } from '@core/roles';
import { PageHeader } from '@shared/components/page-header/page-header';
import { AnneeRequise } from '@shared/composants/annee-requise';
import { enIso } from '@shared/composants/dates';
import { EtatListe } from '@shared/composants/etat-liste';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { ImportFichier } from '@shared/composants/import-fichier';

@Component({
  selector: 'app-eleves',
  templateUrl: './eleves.html',
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
    MatTooltipModule,
    MatDatepickerModule,
    MatSortModule,
    MatPaginatorModule,
    MtxSelectModule,
    MatTabsModule,
    PageHeader,
    BoutonFormulaire,
    AnneeRequise,
    ImportFichier,
  ],
})
export class Eleves {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  protected readonly contexte = inject(AnneeContexte);

  protected readonly peutInscrire = this.auth.aUnRole(...SCOLARITE);
  protected readonly peutCorrigerCategorie = this.auth.aUnRole('INTENDANT');

  protected readonly colonnes = this.peutCorrigerCategorie
    ? ['matricule', 'nom', 'naissance', 'sexe', 'categorie', 'redoublant', 'actions']
    : ['matricule', 'nom', 'naissance', 'sexe', 'categorie', 'redoublant'];

  protected readonly classes = signal<Classe[]>([]);
  protected readonly classeId = signal<string | null>(null);
  protected readonly classe = computed(
    () => this.classes().find((c) => c.id === this.classeId()) ?? null,
  );
  protected readonly inscriptions = signal<Inscription[]>([]);
  /** Page, taille et tri : la pagination se fait côté serveur. */
  protected readonly liste = new EtatListe(25, 'eleve.nom,asc');
  protected readonly recherche = signal('');
  protected readonly statutFiltre = signal<string | null>('ACTIVE');
  protected readonly redoublantFiltre = signal<boolean | null>(null);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected nouveau = this.vide();
  protected correction: { inscriptionId: string; categorie: string; motif: string } | null = null;

  constructor() {
    effect(() => {
      const anneeId = this.contexte.anneeId();
      this.classeId.set(null);
      this.inscriptions.set([]);
      if (anneeId) {
        this.chargerClasses(anneeId);
      } else {
        this.classes.set([]);
      }
    });
  }

  protected choisirClasse(id: string | null): void {
    this.classeId.set(id);
    this.correction = null;
    this.erreur.set('');
    this.liste.auDebut();
    this.chargerInscriptions();
  }

  /** Recherche au clavier : on attend une pause avant d'appeler l'API. */
  protected chercher(terme: string): void {
    this.recherche.set(terme);
    this.liste.differer(() => {
      this.liste.auDebut();
      this.chargerInscriptions();
    });
  }

  protected filtrer(): void {
    this.liste.auDebut();
    this.chargerInscriptions();
  }

  protected viderFiltres(): void {
    this.recherche.set('');
    this.statutFiltre.set(null);
    this.redoublantFiltre.set(null);
    this.filtrer();
  }

  protected readonly filtresActifs = () =>
    this.recherche() !== '' || this.statutFiltre() !== 'ACTIVE' || this.redoublantFiltre() !== null;

  /** La fenêtre ne se referme qu'une fois l'élève inscrit. */
  protected inscrire(fenetre?: BoutonFormulaire): void {
    const classeId = this.classeId();
    if (!classeId) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    // Le calendrier rend un objet Date : l'API attend « 2012-03-15 ».
    this.api
      .inscrire(classeId, {
        ...this.nouveau,
        dateNaissance: enIso(this.nouveau.dateNaissance) ?? '',
      })
      .subscribe({
        next: () => {
          this.nouveau = this.vide();
          this.enCours.set(false);
          fenetre?.fermer();
          this.rafraichir();
        },
        error: (e) => {
          this.erreur.set(messageErreur(e));
          this.enCours.set(false);
        },
      });
  }

  protected ouvrirCorrection(i: Inscription): void {
    this.correction = {
      inscriptionId: i.id,
      categorie: i.categorie === 'NOUVEAU' ? 'ANCIEN' : 'NOUVEAU',
      motif: '',
    };
  }

  protected enregistrerCorrection(): void {
    const c = this.correction;
    if (!c || !c.motif.trim()) {
      return;
    }
    this.erreur.set('');
    this.api.corrigerCategorie(c.inscriptionId, c.categorie, c.motif).subscribe({
      next: () => {
        this.correction = null;
        this.chargerInscriptions();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  /** Après une inscription ou un import : la liste et les effectifs changent. */
  protected rafraichir(): void {
    this.chargerInscriptions();
    const anneeId = this.contexte.anneeId();
    if (anneeId) {
      this.chargerClasses(anneeId);
    }
  }

  private chargerClasses(anneeId: string): void {
    this.api.classes(anneeId).subscribe({
      next: (classes) => this.classes.set(classes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected chargerInscriptions(): void {
    const classeId = this.classeId();
    if (!classeId) {
      this.inscriptions.set([]);
      this.liste.total.set(0);
      return;
    }
    this.liste.chargement.set(true);
    this.api
      .inscriptions(classeId, {
        ...this.liste.parametres(),
        recherche: this.recherche(),
        statut: this.statutFiltre(),
        redoublant: this.redoublantFiltre(),
      })
      .subscribe({
        next: (page) => {
          this.inscriptions.set(this.liste.recevoir(page));
          this.liste.chargement.set(false);
        },
        error: (e) => {
          this.erreur.set(messageErreur(e));
          this.liste.chargement.set(false);
        },
      });
  }

  private vide() {
    return {
      matricule: '',
      nom: '',
      prenom: '',
      dateNaissance: null as Date | null,
      sexe: 'F',
      nomParent: '',
      contactParent: '',
      redoublant: false,
    };
  }
}
