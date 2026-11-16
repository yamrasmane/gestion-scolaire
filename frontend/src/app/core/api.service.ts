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

export type Cycle = 'PREMIER' | 'SECOND';

export interface CompositionEffective {
  avecComposition: boolean;
  poidsComposition: number;
  source: 'CYCLE' | 'TRIMESTRE' | 'ANNEE';
}

export interface Trimestre {
  id: number;
  numero: number;
  dateDebut: string;
  dateFin: string;
  avecComposition: boolean | null;
  poidsComposition: number | null;
  cloture: boolean;
  compositionParCycle: Record<Cycle, CompositionEffective>;
}

export interface Niveau {
  id: number;
  code: string;
  libelle: string;
  cycle: Cycle;
  ordre: number;
}

export interface Classe {
  id: number;
  niveauId: number;
  niveauCode: string;
  cycle: Cycle;
  nom: string;
  effectifMax: number;
  effectif: number;
}

export interface Personnel {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  email: string;
  telephone: string | null;
  role: string;
  actif: boolean;
  compteCree: boolean;
}

export interface ResultatPersonnel {
  personnel: Personnel;
  avertissement: string | null;
}

export interface Eleve {
  id: number;
  matricule: string;
  nom: string;
  prenom: string;
  dateNaissance: string;
  sexe: 'M' | 'F';
  nomParent: string | null;
  contactParent: string | null;
}

export interface Inscription {
  id: number;
  eleve: Eleve;
  classeId: number;
  categorie: 'NOUVEAU' | 'ANCIEN';
  categorieForcee: boolean;
  motifCategorie: string | null;
  redoublant: boolean;
  statut: string;
}

/** Résultat d'un import en deux étapes (simulation puis import définitif). */
export interface RapportImport {
  simulation: boolean;
  importe: boolean;
  totalLignes: number;
  erreurs: { ligne: number; message: string }[];
  avertissements: string[];
  lignes: Record<string, string | number | boolean>[];
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

  // ---------- Trimestres et composition ----------

  trimestres(anneeId: number): Observable<Trimestre[]> {
    return this.http.get<Trimestre[]>(`${this.base}/annees-scolaires/${anneeId}/trimestres`);
  }

  creerTrimestre(anneeId: number, t: { numero: number; dateDebut: string; dateFin: string }): Observable<Trimestre> {
    return this.http.post<Trimestre>(`${this.base}/annees-scolaires/${anneeId}/trimestres`, t);
  }

  reglerComposition(
    trimestreId: number,
    reglage: { avecComposition: boolean | null; poidsComposition: number | null },
  ): Observable<Trimestre> {
    return this.http.put<Trimestre>(`${this.base}/trimestres/${trimestreId}/composition`, reglage);
  }

  reglerCompositionCycle(
    trimestreId: number,
    cycle: Cycle,
    reglage: { avecComposition: boolean; poidsComposition: number },
  ): Observable<Trimestre> {
    return this.http.put<Trimestre>(`${this.base}/trimestres/${trimestreId}/composition/${cycle}`, reglage);
  }

  supprimerReglageCycle(trimestreId: number, cycle: Cycle): Observable<Trimestre> {
    return this.http.delete<Trimestre>(`${this.base}/trimestres/${trimestreId}/composition/${cycle}`);
  }

  cloturerTrimestre(trimestreId: number): Observable<Trimestre> {
    return this.http.post<Trimestre>(`${this.base}/trimestres/${trimestreId}/cloture`, null);
  }

  rouvrirTrimestre(trimestreId: number): Observable<Trimestre> {
    return this.http.post<Trimestre>(`${this.base}/trimestres/${trimestreId}/reouverture`, null);
  }

  // ---------- Niveaux et classes ----------

  niveaux(): Observable<Niveau[]> {
    return this.http.get<Niveau[]>(`${this.base}/niveaux`);
  }

  creerNiveau(n: { code: string; libelle: string; cycle: Cycle; ordre: number }): Observable<Niveau> {
    return this.http.post<Niveau>(`${this.base}/niveaux`, n);
  }

  classes(anneeId: number): Observable<Classe[]> {
    return this.http.get<Classe[]>(`${this.base}/annees-scolaires/${anneeId}/classes`);
  }

  creerClasse(anneeId: number, c: { niveauId: number; nom: string; effectifMax: number }): Observable<Classe> {
    return this.http.post<Classe>(`${this.base}/annees-scolaires/${anneeId}/classes`, c);
  }

  // ---------- Personnel ----------

  personnel(): Observable<Personnel[]> {
    return this.http.get<Personnel[]>(`${this.base}/personnel`);
  }

  creerPersonnel(p: {
    matricule: string;
    nom: string;
    prenom: string;
    email: string;
    telephone: string;
    role: string;
  }): Observable<ResultatPersonnel> {
    return this.http.post<ResultatPersonnel>(`${this.base}/personnel`, p);
  }

  activerPersonnel(id: number, actif: boolean): Observable<Personnel> {
    return this.http.post<Personnel>(`${this.base}/personnel/${id}/${actif ? 'activation' : 'desactivation'}`, null);
  }

  // ---------- Élèves et inscriptions ----------

  inscriptions(classeId: number): Observable<Inscription[]> {
    return this.http.get<Inscription[]>(`${this.base}/classes/${classeId}/inscriptions`);
  }

  inscrire(
    classeId: number,
    eleve: {
      matricule: string;
      nom: string;
      prenom: string;
      dateNaissance: string;
      sexe: string;
      nomParent: string;
      contactParent: string;
      redoublant: boolean;
    },
  ): Observable<Inscription> {
    return this.http.post<Inscription>(`${this.base}/classes/${classeId}/inscriptions`, eleve);
  }

  corrigerCategorie(inscriptionId: number, categorie: string, motif: string): Observable<Inscription> {
    return this.http.put<Inscription>(`${this.base}/inscriptions/${inscriptionId}/categorie`, { categorie, motif });
  }

  // ---------- Imports Excel / CSV ----------

  /** @param chemin chemin de l'API d'import, par exemple `/personnel/import` */
  importer(chemin: string, fichier: File, simulation: boolean): Observable<RapportImport> {
    const corps = new FormData();
    corps.append('fichier', fichier);
    return this.http.post<RapportImport>(`${this.base}${chemin}?simulation=${simulation}`, corps);
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
