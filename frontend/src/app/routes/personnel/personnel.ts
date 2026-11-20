import { Component, TemplateRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule } from '@angular/material/sort';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';

import { ApiService, Personnel as Membre, messageErreur } from '@core/api.service';
import { LIBELLES_ROLES, Role } from '@core/roles';
import { PageHeader } from '@shared/components/page-header/page-header';
import { EtatListe } from '@shared/composants/etat-liste';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { ImportFichier } from '@shared/composants/import-fichier';

@Component({
  selector: 'app-personnel',
  templateUrl: './personnel.html',
  imports: [
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSortModule,
    MatPaginatorModule,
    MatTabsModule,
    MatDialogModule,
    PageHeader,
    ImportFichier,
    BoutonFormulaire,
  ],
})
export class Personnel {
  private readonly api = inject(ApiService);
  private readonly fenetres = inject(MatDialog);

  /** La fiche à modifier s'ouvre dans une fenêtre, pas dans la page. */
  private readonly gabaritEdition = viewChild<TemplateRef<unknown>>('edition');
  private fenetreEdition?: MatDialogRef<unknown>;

  protected readonly roles = Object.entries(LIBELLES_ROLES) as [Role, string][];
  protected readonly colonnes = [
    'matricule',
    'nom',
    'email',
    'telephone',
    'role',
    'etat',
    'actions',
  ];
  protected readonly membres = signal<Membre[]>([]);
  /** Page, taille et tri de la liste : la pagination se fait côté serveur. */
  protected readonly liste = new EtatListe(25, 'nom,asc');
  protected readonly recherche = signal('');
  protected readonly roleFiltre = signal<string | null>(null);
  protected readonly actifFiltre = signal<boolean | null>(null);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  protected nouveau = this.vide();
  /** Fiche en cours de modification : la même carte sert aux deux gestes. */
  protected readonly enEdition = signal<Membre | null>(null);
  protected modification = this.videModification();

  constructor() {
    this.charger();
  }

  protected libelleRole(role: string): string {
    return LIBELLES_ROLES[role as Role] ?? role;
  }

  /** La fenêtre ne se referme qu'une fois la fiche créée. */
  protected creer(fenetre?: BoutonFormulaire): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    this.api.creerPersonnel(this.nouveau).subscribe({
      next: (resultat) => {
        this.information.set(resultat.avertissement ?? '');
        this.nouveau = this.vide();
        this.enCours.set(false);
        fenetre?.fermer();
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected editer(membre: Membre): void {
    this.enEdition.set(membre);
    this.erreur.set('');
    this.information.set('');
    this.modification = {
      nom: membre.nom,
      prenom: membre.prenom,
      email: membre.email,
      telephone: membre.telephone ?? '',
      role: membre.role,
    };
    const gabarit = this.gabaritEdition();
    if (gabarit) {
      this.fenetreEdition = this.fenetres.open(gabarit, {
        width: '960px',
        maxWidth: '96vw',
        maxHeight: '95vh',
        disableClose: true,
      });
      this.fenetreEdition.keydownEvents().subscribe((evenement) => {
        if (evenement.key === 'Escape') {
          this.abandonner();
        }
      });
    }
  }

  protected abandonner(): void {
    this.fenetreEdition?.close();
    this.enEdition.set(null);
    this.modification = this.videModification();
  }

  protected enregistrer(): void {
    const membre = this.enEdition();
    if (!membre) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    this.api.modifierPersonnel(membre.id, this.modification).subscribe({
      next: (fiche) => {
        this.information.set(`La fiche de ${fiche.nom} ${fiche.prenom} a été mise à jour.`);
        this.abandonner();
        this.enCours.set(false);
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected basculer(membre: Membre): void {
    this.erreur.set('');
    this.api.activerPersonnel(membre.id, !membre.actif).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  /** Recherche au clavier : on attend une pause avant d'appeler l'API. */
  protected chercher(terme: string): void {
    this.recherche.set(terme);
    this.liste.differer(() => {
      this.liste.auDebut();
      this.charger();
    });
  }

  protected filtrer(): void {
    this.liste.auDebut();
    this.charger();
  }

  protected viderFiltres(): void {
    this.recherche.set('');
    this.roleFiltre.set(null);
    this.actifFiltre.set(null);
    this.filtrer();
  }

  protected readonly filtresActifs = () =>
    this.recherche() !== '' || this.roleFiltre() !== null || this.actifFiltre() !== null;

  protected charger(): void {
    this.liste.chargement.set(true);
    this.api
      .personnel({
        ...this.liste.parametres(),
        recherche: this.recherche(),
        role: this.roleFiltre(),
        actif: this.actifFiltre(),
      })
      .subscribe({
        next: (page) => {
          this.membres.set(this.liste.recevoir(page));
          this.liste.chargement.set(false);
        },
        error: (e) => {
          this.erreur.set(messageErreur(e));
          this.liste.chargement.set(false);
        },
      });
  }

  private vide() {
    return { matricule: '', nom: '', prenom: '', email: '', telephone: '', role: 'ENSEIGNANT' };
  }

  private videModification() {
    return { nom: '', prenom: '', email: '', telephone: '', role: 'ENSEIGNANT' };
  }
}
