import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { ApiService, messageErreur } from '@core/api.service';
import { ApercuDocument, DemandeApercu } from './apercu-document';

/** Formats proposés, dans l'ordre où ils sont offerts. */
const FORMATS: { code: string; libelle: string; icone: string }[] = [
  { code: 'pdf', libelle: 'PDF', icone: 'picture_as_pdf' },
  { code: 'xlsx', libelle: 'Excel', icone: 'table_view' },
  { code: 'docx', libelle: 'Word', icone: 'description' },
  { code: 'csv', libelle: 'CSV', icone: 'list_alt' },
];

/**
 * Bouton d'édition d'un document. Le bouton principal ouvre l'aperçu avant
 * impression ; le menu à côté enregistre directement dans le format voulu,
 * pour qui sait déjà ce qu'il veut. Les documents sont fabriqués par le
 * serveur à partir des modèles Jasper et transférés avec le jeton de session.
 */
@Component({
  selector: 'app-bouton-document',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <span class="ligne-actions">
      <button matButton="filled" [disabled]="enCours()" (click)="apercu()">
        <mat-icon>print</mat-icon>
        {{ libelle() }}
      </button>
      <button
        matIconButton
        [matMenuTriggerFor]="menu"
        [disabled]="enCours()"
        aria-label="Aperçu ou enregistrement dans un autre format"
      >
        <mat-icon>more_vert</mat-icon>
      </button>
      <mat-menu #menu="matMenu">
        <button mat-menu-item (click)="apercu()">
          <mat-icon>visibility</mat-icon>
          <span>Aperçu avant impression</span>
        </button>
        @for (f of formats; track f.code) {
          <button mat-menu-item (click)="editer(f.code)">
            <mat-icon>{{ f.icone }}</mat-icon>
            <span>Enregistrer en {{ f.libelle }}</span>
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
  private readonly fenetres = inject(MatDialog);

  /** Adresse du document, sans le préfixe « /api/rapports ». */
  readonly chemin = input.required<string>();
  /** Nom du fichier enregistré, sans extension. */
  readonly nom = input<string>('document');
  readonly libelle = input<string>('Imprimer');
  /** Format mis en tête du menu d'enregistrement ; l'aperçu reste en PDF. */
  readonly formatDefaut = input<string>('pdf');
  /** Critères transmis au serveur (classe, date, recherche…). */
  readonly criteres = input<Record<string, unknown>>({});

  protected readonly formats = FORMATS;
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');

  /** Ouvre l'aperçu avant impression, d'où l'on imprime ou enregistre. */
  protected apercu(): void {
    this.erreur.set('');
    const demande: DemandeApercu = {
      chemin: this.chemin(),
      nom: this.nom(),
      titre: this.libelle(),
      criteres: this.criteres(),
      // Le format mis en avant par l'écran est proposé en tête de liste.
      formats: [...this.formats].sort(
        (a, b) => Number(b.code === this.formatDefaut()) - Number(a.code === this.formatDefaut()),
      ),
    };
    this.fenetres.open(ApercuDocument, {
      data: demande,
      width: '900px',
      maxWidth: '96vw',
      maxHeight: '94vh',
      autoFocus: false,
      restoreFocus: true,
    });
  }

  /** Enregistrement direct, sans passer par l'aperçu. */
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
