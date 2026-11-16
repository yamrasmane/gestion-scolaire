import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ApiService, RapportImport, messageErreur } from './api.service';

/**
 * Import Excel/CSV en deux étapes : « Analyser » affiche l'aperçu et les
 * erreurs sans rien enregistrer, puis « Importer » enregistre si tout est valide.
 */
@Component({
  selector: 'app-import-fichier',
  template: `
    <p class="discret">{{ aide() }}</p>
    <div class="actions">
      <input type="file" accept=".xlsx,.xls,.csv" (change)="choisir($event)" aria-label="Fichier à importer" />
      <button type="button" class="secondaire" [disabled]="!fichier() || enCours()" (click)="envoyer(true)">
        Analyser
      </button>
      <button type="button" [disabled]="!importable() || enCours()" (click)="envoyer(false)">Importer</button>
    </div>

    @if (erreur(); as message) {
      <p class="erreur" role="alert">{{ message }}</p>
    }

    @if (rapport(); as r) {
      @if (r.importe) {
        <p><span class="badge succes">Import réussi</span> {{ r.totalLignes }} ligne(s) enregistrée(s).</p>
      } @else if (r.erreurs.length > 0) {
        <p class="erreur">
          {{ r.erreurs.length }} erreur(s) : rien n'a été importé. Corrigez le fichier puis relancez l'analyse.
        </p>
        <table>
          <thead>
            <tr>
              <th>Ligne</th>
              <th>Problème</th>
            </tr>
          </thead>
          <tbody>
            @for (e of r.erreurs; track $index) {
              <tr>
                <td>{{ e.ligne }}</td>
                <td>{{ e.message }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <p>
          <span class="badge succes">Fichier valide</span>
          {{ r.totalLignes }} ligne(s) prête(s). Cliquez sur « Importer » pour les enregistrer.
        </p>
      }
      @for (a of r.avertissements; track $index) {
        <p class="discret">{{ a }}</p>
      }
      @if (!r.importe && r.lignes.length > 0) {
        <details>
          <summary>Aperçu des {{ r.lignes.length }} ligne(s) lues</summary>
          <div class="defilement">
            <table>
              <thead>
                <tr>
                  @for (c of colonnes(); track c) {
                    <th>{{ c }}</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (l of r.lignes; track $index) {
                  <tr>
                    @for (c of colonnes(); track c) {
                      <td>{{ afficher(l[c]) }}</td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </details>
      }
    }
  `,
})
export class ImportFichier {
  private readonly api = inject(ApiService);

  /** Chemin de l'API d'import, par exemple `/personnel/import`. */
  readonly chemin = input.required<string>();
  /** Rappel des colonnes attendues. */
  readonly aide = input('');
  /** Émis quand des lignes ont été enregistrées. */
  readonly termine = output<void>();

  protected readonly fichier = signal<File | null>(null);
  protected readonly rapport = signal<RapportImport | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');

  /** L'import n'est proposé qu'après une analyse sans erreur du fichier choisi. */
  protected readonly importable = computed(() => {
    const r = this.rapport();
    return !!this.fichier() && !!r && r.simulation && r.erreurs.length === 0;
  });
  protected readonly colonnes = computed(() => Object.keys(this.rapport()?.lignes[0] ?? {}));

  protected choisir(evenement: Event): void {
    this.fichier.set((evenement.target as HTMLInputElement).files?.[0] ?? null);
    this.rapport.set(null);
    this.erreur.set('');
  }

  protected envoyer(simulation: boolean): void {
    const fichier = this.fichier();
    if (!fichier) {
      return;
    }
    this.enCours.set(true);
    this.erreur.set('');
    this.api.importer(this.chemin(), fichier, simulation).subscribe({
      next: (rapport) => {
        this.rapport.set(rapport);
        this.enCours.set(false);
        if (rapport.importe) {
          this.termine.emit();
        }
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected afficher(valeur: string | number | boolean): string {
    return typeof valeur === 'boolean' ? (valeur ? 'oui' : 'non') : String(valeur ?? '');
  }
}
