import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';

/** Texte d'une demande de confirmation. */
export interface DemandeConfirmation {
  titre: string;
  /** Ce que l'action va faire, en une ou deux phrases. */
  message: string;
  /** Libellé du bouton qui confirme ; « Supprimer » par défaut. */
  action?: string;
  /** Conséquence à signaler en plus, quand il y en a une. */
  precision?: string;
}

/**
 * Fenêtre de confirmation d'une action irréversible. Elle ne se ferme pas au
 * clic à côté : il faut répondre.
 */
@Component({
  selector: 'app-confirmation',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>{{ demande.titre }}</h2>
    <mat-dialog-content>
      <p>{{ demande.message }}</p>
      @if (demande.precision) {
        <p class="text-muted">{{ demande.precision }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions>
      <button matButton type="button" [mat-dialog-close]="false">Annuler</button>
      <button matButton="filled" type="button" class="bouton-danger" [mat-dialog-close]="true">
        <mat-icon>delete</mat-icon>
        {{ demande.action ?? 'Supprimer' }}
      </button>
    </mat-dialog-actions>
  `,
})
export class Confirmation {
  protected readonly demande = inject<DemandeConfirmation>(MAT_DIALOG_DATA);
}

/**
 * Demande confirmation avant une action irréversible.
 *
 * ```ts
 * private readonly confirmer = confirmation();
 * …
 * this.confirmer({ titre: 'Supprimer la matière', message: '…' })
 *   .subscribe(() => this.api.supprimerMatiere(id).subscribe());
 * ```
 *
 * L'observable n'émet que si la personne confirme : la suite du traitement
 * s'écrit donc sans condition à relire.
 */
export function confirmation(): (demande: DemandeConfirmation) => Observable<boolean> {
  const fenetres = inject(MatDialog);
  return (demande) =>
    new Observable<boolean>((abonne) => {
      const reference = fenetres.open(Confirmation, {
        data: demande,
        width: '520px',
        maxWidth: '94vw',
        disableClose: true,
        autoFocus: 'dialog',
      });
      const abonnement = reference.afterClosed().subscribe((confirme) => {
        if (confirme) {
          abonne.next(true);
        }
        abonne.complete();
      });
      return () => abonnement.unsubscribe();
    });
}
