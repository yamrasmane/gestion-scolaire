import { NgTemplateOutlet } from '@angular/common';
import { Component, TemplateRef, contentChild, inject, input, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

/**
 * Bouton qui ouvre un formulaire dans une fenêtre, comme les « + Nouveau… »
 * des maquettes : la page ne montre que ce qui se consulte, la saisie arrive
 * à la demande.
 *
 * Le formulaire est passé en contenu, dans un `ng-template` : il garde le
 * contexte de l'écran qui le déclare, donc ses champs et ses actions
 * fonctionnent sans changement. Le contexte expose la fenêtre, pour fermer
 * après un enregistrement réussi :
 *
 * ```html
 * <app-bouton-formulaire libelle="Ajouter une personne">
 *   <ng-template let-fenetre>
 *     <form (ngSubmit)="creer(); fenetre.fermer()"> … </form>
 *   </ng-template>
 * </app-bouton-formulaire>
 * ```
 */
@Component({
  selector: 'app-bouton-formulaire',
  imports: [NgTemplateOutlet, MatButtonModule, MatIconModule, MatDialogModule],
  template: `
    <button matButton="filled" type="button" (click)="ouvrir()">
      <mat-icon>{{ icone() }}</mat-icon>
      {{ libelle() }}
    </button>

    <ng-template #fenetre>
      <h2 mat-dialog-title>{{ titre() || libelle() }}</h2>
      <mat-dialog-content>
        <ng-container
          [ngTemplateOutlet]="contenu()!"
          [ngTemplateOutletContext]="{ $implicit: this }"
        />
      </mat-dialog-content>
      <mat-dialog-actions>
        <button matButton mat-dialog-close type="button">Fermer</button>
        @if (formulaire()) {
          <button matButton="filled" type="submit" [attr.form]="formulaire()">
            <mat-icon>{{ icone() }}</mat-icon>
            {{ action() }}
          </button>
        }
      </mat-dialog-actions>
    </ng-template>
  `,
})
export class BoutonFormulaire {
  private readonly fenetres = inject(MatDialog);

  readonly libelle = input('Ajouter');
  /** Titre de la fenêtre ; le libellé du bouton par défaut. */
  readonly titre = input('');
  readonly icone = input('add');
  readonly largeur = input('780px');
  /**
   * Identifiant du `form` projeté. Renseigné, le bouton de validation est
   * rendu dans la barre d'actions de la fenêtre, comme sur les maquettes.
   */
  readonly formulaire = input('');
  readonly action = input('Enregistrer');

  /** Le formulaire projeté par l'écran. */
  protected readonly contenu = contentChild(TemplateRef);
  private readonly gabarit = viewChild<TemplateRef<unknown>>('fenetre');
  private reference?: MatDialogRef<unknown>;

  protected ouvrir(): void {
    const gabarit = this.gabarit();
    if (!gabarit) {
      return;
    }
    this.reference = this.fenetres.open(gabarit, {
      width: this.largeur(),
      maxWidth: '96vw',
      maxHeight: '92vh',
      autoFocus: 'first-tabbable',
    });
  }

  /** Ferme la fenêtre : à appeler après un enregistrement réussi. */
  fermer(): void {
    this.reference?.close();
  }
}
