import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { ApiService, messageErreur } from '@core/api.service';

/** Ce qu'il faut pour éditer le document dont on montre l'aperçu. */
export interface DemandeApercu {
  /** Adresse du document, sans le préfixe « /api/rapports ». */
  chemin: string;
  /** Nom du fichier enregistré, sans extension. */
  nom: string;
  /** Titre de la fenêtre. */
  titre: string;
  /** Critères transmis au serveur (classe, date, recherche…). */
  criteres: Record<string, unknown>;
  /** Formats offerts au téléchargement. */
  formats: { code: string; libelle: string; icone: string }[];
}

/**
 * Aperçu avant impression : le PDF produit par le serveur est affiché dans la
 * fenêtre, où il peut être parcouru, imprimé, ou enregistré dans un autre
 * format. L'aperçu est toujours le PDF, seul format qui rende la mise en page
 * du modèle Jasper telle qu'elle sortira de l'imprimante.
 */
@Component({
  selector: 'app-apercu-document',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatMenuModule, MatProgressBarModule],
  template: `
    <h2 mat-dialog-title>{{ demande.titre }}</h2>

    <mat-dialog-content class="apercu-contenu">
      @if (chargement()) {
        <mat-progress-bar mode="indeterminate" />
        <p class="text-muted">Édition du document…</p>
      }
      @if (erreur(); as message) {
        <p class="alerte-erreur" role="alert">{{ message }}</p>
      }
      @if (apercu(); as source) {
        <iframe #cadre class="apercu-cadre" [src]="source" [title]="demande.titre"></iframe>
      }
    </mat-dialog-content>

    <mat-dialog-actions class="apercu-actions">
      <button matButton mat-dialog-close>Fermer</button>
      <span class="separateur"></span>
      <button matButton [disabled]="!apercu() || telechargement()" [matMenuTriggerFor]="menu">
        <mat-icon>download</mat-icon>
        Enregistrer
      </button>
      <mat-menu #menu="matMenu">
        @for (f of demande.formats; track f.code) {
          <button mat-menu-item (click)="telecharger(f.code)">
            <mat-icon>{{ f.icone }}</mat-icon>
            <span>{{ f.libelle }}</span>
          </button>
        }
      </mat-menu>
      <button matButton="filled" [disabled]="!apercu()" (click)="imprimer()">
        <mat-icon>print</mat-icon>
        Imprimer
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .apercu-contenu {
      display: flex;
      flex-direction: column;
      min-height: 78vh;
      max-height: 82vh;
      padding-top: 0;
    }

    .apercu-cadre {
      flex: 1 1 auto;
      width: 100%;
      min-height: 78vh;
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 4px;
    }

    .apercu-actions .separateur {
      flex: 1 1 auto;
    }

    @media (width <= 599px) {
      .apercu-contenu,
      .apercu-cadre {
        min-height: 62vh;
      }
    }
  `,
})
export class ApercuDocument implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly nettoyeur = inject(DomSanitizer);
  private readonly fenetre = inject<MatDialogRef<ApercuDocument>>(MatDialogRef);
  protected readonly demande = inject<DemandeApercu>(MAT_DIALOG_DATA);

  private readonly cadre = viewChild<ElementRef<HTMLIFrameElement>>('cadre');
  /** Adresse temporaire du PDF affiché, à libérer à la fermeture. */
  private adresse: string | null = null;

  protected readonly apercu = signal<SafeResourceUrl | null>(null);
  protected readonly chargement = signal(true);
  protected readonly telechargement = signal(false);
  protected readonly erreur = signal('');

  constructor() {
    this.api.document(this.demande.chemin, { ...this.demande.criteres, format: 'pdf' }).subscribe({
      next: (fichier) => {
        this.adresse = URL.createObjectURL(fichier);
        // Le visualiseur du navigateur ouvre le PDF sans barre d'outils en
        // double : on garde la nôtre, en bas de la fenêtre.
        this.apercu.set(
          this.nettoyeur.bypassSecurityTrustResourceUrl(`${this.adresse}#toolbar=0&navpanes=0`),
        );
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  /** Imprime l'aperçu lui-même : ce qui sort est exactement ce qui est montré. */
  protected imprimer(): void {
    const fenetreCadre = this.cadre()?.nativeElement.contentWindow;
    if (!fenetreCadre) {
      this.erreur.set("L'aperçu n'est pas encore prêt.");
      return;
    }
    fenetreCadre.focus();
    fenetreCadre.print();
  }

  protected telecharger(format: string): void {
    this.telechargement.set(true);
    this.erreur.set('');
    this.api.document(this.demande.chemin, { ...this.demande.criteres, format }).subscribe({
      next: (fichier) => {
        const adresse = URL.createObjectURL(fichier);
        const lien = document.createElement('a');
        lien.href = adresse;
        lien.download = `${this.demande.nom}.${format}`;
        lien.click();
        URL.revokeObjectURL(adresse);
        this.telechargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.telechargement.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    if (this.adresse) {
      URL.revokeObjectURL(this.adresse);
    }
  }

  /** Permet de fermer depuis l'extérieur (tests, raccourcis). */
  fermer(): void {
    this.fenetre.close();
  }
}
