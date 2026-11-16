import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { config } from './config';

export interface Profil {
  identifiant: string;
  nomUtilisateur: string;
  nomComplet: string | null;
  email: string | null;
  roles: string[];
}

export interface AnneeScolaire {
  id: number;
  libelle: string;
  dateDebut: string;
  dateFin: string;
  avecCompositionDefaut: boolean;
  poidsCompositionDefaut: number;
  bloquerBulletinImpaye: boolean;
  actif: boolean;
  createdBy: string;
}

export interface NouvelleAnneeScolaire {
  libelle: string;
  dateDebut: string;
  dateFin: string;
  avecCompositionDefaut: boolean;
  poidsCompositionDefaut: number;
  bloquerBulletinImpaye: boolean;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = config.apiUrl;

  profil(): Observable<Profil> {
    return this.http.get<Profil>(`${this.base}/moi`);
  }

  anneesScolaires(): Observable<AnneeScolaire[]> {
    return this.http.get<AnneeScolaire[]>(`${this.base}/annees-scolaires`);
  }

  creerAnneeScolaire(annee: NouvelleAnneeScolaire): Observable<AnneeScolaire> {
    return this.http.post<AnneeScolaire>(`${this.base}/annees-scolaires`, annee);
  }

  activerAnneeScolaire(id: number): Observable<AnneeScolaire> {
    return this.http.post<AnneeScolaire>(`${this.base}/annees-scolaires/${id}/activation`, null);
  }
}

/** Message lisible à partir d'une erreur renvoyée par l'API (format problem+json). */
export function messageErreur(erreur: unknown): string {
  if (erreur instanceof HttpErrorResponse) {
    if (erreur.status === 0) {
      return "Le serveur est injoignable. Vérifiez que l'API est démarrée.";
    }
    if (erreur.status === 403) {
      return "Vous n'avez pas les droits nécessaires pour cette action.";
    }
    return erreur.error?.detail ?? `Erreur ${erreur.status}`;
  }
  return 'Une erreur inattendue est survenue.';
}
