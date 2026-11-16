import { Injectable, computed, inject, signal } from '@angular/core';
import { AnneeScolaire, ApiService } from './api.service';

/**
 * Année scolaire de travail, choisie dans le bandeau et partagée par toutes
 * les pages. Par défaut : l'année active, sinon la plus récente.
 */
@Injectable({ providedIn: 'root' })
export class AnneeContexte {
  private readonly api = inject(ApiService);

  private readonly _annees = signal<AnneeScolaire[]>([]);
  private readonly _anneeId = signal<number | null>(null);

  readonly annees = this._annees.asReadonly();
  readonly anneeId = this._anneeId.asReadonly();
  readonly annee = computed(() => this._annees().find((a) => a.id === this._anneeId()) ?? null);

  charger(): void {
    this.api.anneesScolaires().subscribe({
      next: (annees) => {
        this._annees.set(annees);
        if (!annees.some((a) => a.id === this._anneeId())) {
          this._anneeId.set((annees.find((a) => a.actif) ?? annees[0])?.id ?? null);
        }
      },
      error: () => undefined,
    });
  }

  choisir(id: number): void {
    this._anneeId.set(id);
  }
}
