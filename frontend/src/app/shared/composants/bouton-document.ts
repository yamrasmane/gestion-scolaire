import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { ApiService, messageErreur } from '@core/api.service';

/** Formats proposés, dans l'ordre où ils sont offerts. */
const FORMATS: { code: string; libelle: string; icone: string }[] = [
  { code: 'pdf', libelle: 'PDF', icone: 'picture_as_pdf' },
  { code: 'xlsx', libelle: 'Excel', icone: 'table_view' },
  { code: 'docx', libelle: 'Word', icone: 'description' },
  { code: 'csv', libelle: 'CSV', icone: 'list_alt' },
];

/**
 * Bouton d'édition d'un document : un clic produit le PDF, le menu déroulant
 * offre les autres formats. Le document est fabriqué par le serveur à partir
 * du modèle Jasper correspondant, et téléchargé avec le jeton de session.
 */
@Component({
  selector: 'app-bouton-document',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <span class="ligne-actions">
      <button matButton="filled" [disabled]="enCours()" (click)="editer(formatDefaut())">
        <mat-icon>print</mat-icon>
        {{ libelle() }}
      </button>
      <button
        matIconButton
        [matMenuTriggerFor]="menu"
        [disabled]="enCours()"
        aria-label="Choisir le format du document"
      >
        <mat-icon>more_vert</mat-icon>
      </button>
      <mat-menu #menu="matMenu">
        @for (f of formats; track f.code) {
          <button mat-menu-item (click)="editer(f.code)">
            <mat-icon>{{ f.icone }}</mat-icon>
            <span>{{ f.libelle }}</span>
          </button>
        }
      </mat-menu>
    </span>
    @if (erreur(); as message) {
      <p class="alerte-erreur" role="alert">{{ message }}</p>
    }
  `,
})
export class BoutonDocument {
  private readonly api = inject(ApiService);

  /** Adresse du document, sans le préfixe « /api/rapports ». */
  readonly chemin = input.required<string>();
  /** Nom du fichier enregistré, sans extension. */
  readonly nom = input<string>('document');
  readonly libelle = input<string>('Imprimer');
  /** Format produit par le bouton principal ; le menu offre les autres. */
  readonly formatDefaut = input<string>('pdf');
  /** Critères transmis au serveur (classe, date, recherche…). */
  readonly criteres = input<Record<string, unknown>>({});

  protected readonly formats = FORMATS;
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');

  protected editer(format: string): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.api.document(this.chemin(), { ...this.criteres(), format }).subscribe({
      next: (fichier) => {
        this.enregistrer(fichier, `${this.nom()}.${format}`);
        this.enCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private enregistrer(fichier: Blob, nomFichier: string): void {
    const adresse = URL.createObjectURL(fichier);
    const lien = document.createElement('a');
    lien.href = adresse;
    lien.download = nomFichier;
    lien.click();
    URL.revokeObjectURL(adresse);
  }
}
