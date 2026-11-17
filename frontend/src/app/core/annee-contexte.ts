import { Injectable, computed, inject, signal } from '@angular/core';
import { AnneeScolaire, ApiService, messageErreur } from './api.service';

/**
 * Année scolaire de travail, choisie dans le bandeau et partagée par toutes
 * les pages. Par défaut : l'année active, sinon la plus récente.
 */
@Injectable({ providedIn: 'root' })
export class AnneeContexte {
  private readonly api = inject(ApiService);

  private readonly _annees = signal<AnneeScolaire[]>([]);
  private readonly _anneeId = signal<string | null>(null);
  private readonly _chargement = signal(true);
  private readonly _erreur = signal('');

  readonly annees = this._annees.asReadonly();
  readonly anneeId = this._anneeId.asReadonly();
  readonly annee = computed(() => this._annees().find((a) => a.id === this._anneeId()) ?? null);

  /** Vrai tant que la liste des années n'a pas été obtenue. */
  readonly chargement = this._chargement.asReadonly();
  /** Message d'erreur si l'API n'a pas pu être jointe : sans cela, une panne
   *  passerait pour une base vide. */
  readonly erreur = this._erreur.asReadonly();

  charger(): void {
    this._chargement.set(true);
    this._erreur.set('');
    this.api.anneesScolaires().subscribe({
      next: (annees) => {
        this._annees.set(annees);
        if (!annees.some((a) => a.id === this._anneeId())) {
          this._anneeId.set((annees.find((a) => a.actif) ?? annees[0])?.id ?? null);
        }
        this._chargement.set(false);
      },
      error: (e) => {
        this._erreur.set(messageErreur(e));
        this._chargement.set(false);
      },
    });
  }

  choisir(id: string): void {
    this._anneeId.set(id);
  }
}
