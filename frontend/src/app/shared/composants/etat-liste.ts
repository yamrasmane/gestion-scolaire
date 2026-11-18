import { signal } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';

import { Page, Pagination } from '@core/api.service';

/**
 * État d'une liste paginée côté serveur : page courante, taille, tri et
 * nombre total de lignes. Les écrans n'ont plus qu'à déclarer leurs critères
 * et à relire `parametres()` pour appeler l'API.
 */
export class EtatListe {
  /** Choix offerts dans le pied de tableau. */
  readonly taillesPage = [10, 25, 50, 100];

  readonly page = signal(0);
  readonly taille = signal(25);
  /** Tri à la façon de Spring : « nom,asc ». */
  readonly tri = signal<string | null>(null);
  readonly total = signal(0);
  readonly chargement = signal(false);

  private minuteur?: ReturnType<typeof setTimeout>;

  constructor(taille = 25, tri: string | null = null) {
    this.taille.set(taille);
    this.tri.set(tri);
  }

  /** Paramètres à passer à l'API. Lire cette méthode suffit à suivre les signaux. */
  parametres(): Pagination {
    return { page: this.page(), taille: this.taille(), tri: this.tri() };
  }

  /** Reprend le total et le numéro de page tels que l'API les a renvoyés. */
  recevoir<T>(page: Page<T>): T[] {
    this.total.set(page.total);
    this.page.set(page.page);
    return page.contenu;
  }

  surPage(evenement: PageEvent): void {
    this.taille.set(evenement.pageSize);
    this.page.set(evenement.pageIndex);
  }

  surTri(tri: Sort): void {
    this.tri.set(tri.direction ? `${tri.active},${tri.direction}` : null);
    this.page.set(0);
  }

  /** Un critère a changé : on revient à la première page. */
  auDebut(): void {
    this.page.set(0);
  }

  /**
   * Applique une saisie au clavier après une courte pause, pour ne pas
   * interroger l'API à chaque frappe.
   */
  differer(action: () => void, delai = 350): void {
    clearTimeout(this.minuteur);
    this.minuteur = setTimeout(action, delai);
  }
}
