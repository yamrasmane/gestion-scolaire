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

// ---------- Finances (lot 3) ----------

export type CategorieTarif = 'NOUVEAU' | 'ANCIEN' | 'TOUS';
export type ModeRepartition = 'POURCENTAGE' | 'MONTANT';
export type TypeRemise = 'POURCENTAGE' | 'MONTANT';
export type ModePaiement = 'ESPECES' | 'MOBILE_MONEY' | 'VIREMENT' | 'CHEQUE';
export type StatutEcheance = 'A_VENIR' | 'PARTIEL' | 'PAYEE' | 'EN_RETARD';

export interface TypeFrais {
  id: number;
  code: string;
  libelle: string;
  obligatoire: boolean;
  fractionnable: boolean;
  prioriteImputation: number;
  actif: boolean;
}

export interface Tarif {
  id: number;
  typeFraisId: number;
  typeFraisCode: string;
  typeFraisLibelle: string;
  niveauId: number | null;
  niveauCode: string | null;
  classeId: number | null;
  classeNom: string | null;
  categorie: CategorieTarif;
  montant: number;
}

export interface Tranche {
  id: number;
  numero: number;
  libelle: string;
  dateEcheance: string;
  pourcentage: number | null;
  montant: number | null;
}

export interface PlanPaiement {
  id: number;
  libelle: string;
  niveauId: number | null;
  niveauCode: string | null;
  classeId: number | null;
  classeNom: string | null;
  categorie: 'NOUVEAU' | 'ANCIEN' | null;
  modeRepartition: ModeRepartition;
  parDefaut: boolean;
  montantMinInscription: number | null;
  tranches: Tranche[];
}

export interface FraisEleve {
  id: number;
  typeFraisId: number;
  typeFraisCode: string;
  typeFraisLibelle: string;
  obligatoire: boolean;
  fractionnable: boolean;
  montantBrut: number;
  remiseType: TypeRemise | null;
  remiseValeur: number | null;
  montantRemise: number;
  montantNet: number;
  motifRemise: string | null;
  remiseValideePar: string | null;
}

export interface Echeance {
  id: number;
  fraisEleveId: number;
  typeFraisLibelle: string;
  trancheNumero: number | null;
  trancheLibelle: string;
  dateEcheance: string;
  montantDu: number;
  montantPaye: number;
  reste: number;
  statut: StatutEcheance;
  derogatoire: boolean;
}

export interface Imputation {
  echeanceId: number;
  typeFraisLibelle: string;
  trancheLibelle: string;
  montant: number;
}

export interface Paiement {
  id: number;
  numeroRecu: string;
  datePaiement: string;
  montant: number;
  mode: ModePaiement;
  reference: string | null;
  payeur: string | null;
  caissier: string | null;
  statut: 'VALIDE' | 'ANNULE';
  motifAnnulation: string | null;
  annulePar: string | null;
  imputations: Imputation[];
}

export interface FicheFinanciere {
  inscriptionId: number;
  matricule: string;
  nom: string;
  prenom: string;
  classeNom: string;
  categorie: 'NOUVEAU' | 'ANCIEN';
  planPaiement: string | null;
  totalBrut: number;
  totalRemise: number;
  totalNet: number;
  totalPaye: number;
  resteAPayer: number;
  echuNonPaye: number;
  anomalies: string[];
  frais: FraisEleve[];
  echeances: Echeance[];
  paiements: Paiement[];
}

export interface Recu {
  numeroRecu: string;
  duplicata: boolean;
  datePaiement: string;
  matricule: string;
  nom: string;
  prenom: string;
  classeNom: string;
  montant: number;
  montantEnLettres: string;
  mode: ModePaiement;
  reference: string | null;
  payeur: string | null;
  caissier: string | null;
  statut: 'VALIDE' | 'ANNULE';
  resteAPayer: number;
  imputations: Imputation[];
}

export interface Impaye {
  inscriptionId: number;
  matricule: string;
  nom: string;
  prenom: string;
  classeNom: string;
  typeFraisLibelle: string;
  trancheLibelle: string;
  dateEcheance: string;
  montantDu: number;
  montantPaye: number;
  reste: number;
  statut: StatutEcheance;
}

export interface JournalCaisse {
  journee: string;
  total: number;
  totauxParMode: { mode: ModePaiement; total: number }[];
  paiements: Paiement[];
}

export interface Recouvrement {
  attendu: number;
  encaisse: number;
  reste: number;
  echuNonPaye: number;
  elevesAJour: number;
  elevesEnRetard: number;
}

// ---------- Notes et bulletins (lot 4) ----------

export type CategorieEvaluation = 'DEVOIR' | 'COMPOSITION';

export interface Matiere {
  id: number;
  code: string;
  libelle: string;
}

export interface Enseignement {
  id: number;
  classeId: number;
  classeNom: string;
  matiereId: number;
  matiereCode: string;
  matiereLibelle: string;
  enseignantId: number | null;
  enseignantNom: string | null;
  coefficient: number;
}

export interface Evaluation {
  id: number;
  enseignementId: number;
  trimestreId: number;
  categorie: CategorieEvaluation;
  libelle: string;
  dateEval: string | null;
  bareme: number;
}

export interface NoteVue {
  inscriptionId: number;
  evaluationId: number;
  valeur: number | null;
  absent: boolean;
  appreciation: string | null;
}

export interface LigneGrille {
  inscriptionId: number;
  matricule: string;
  nom: string;
  prenom: string;
  notes: NoteVue[];
  moyenne: number | null;
  mention: string | null;
}

export interface Grille {
  enseignementId: number;
  matiereLibelle: string;
  classeNom: string;
  trimestreId: number;
  avecComposition: boolean;
  poidsComposition: number;
  trimestreCloture: boolean;
  evaluations: Evaluation[];
  lignes: LigneGrille[];
}

export interface LigneBulletin {
  matiereCode: string;
  matiereLibelle: string;
  enseignant: string | null;
  notesDevoirs: number[];
  moyenneDevoirs: number | null;
  composition: number | null;
  moyenne: number | null;
  coefficient: number;
  moyenneCoefficientee: number | null;
  rang: number | null;
  exAequo: boolean;
  nonClasse: boolean;
  appreciation: string | null;
}

export interface StatistiquesClasse {
  plusForteMoyenne: number | null;
  plusFaibleMoyenne: number | null;
  moyenneClasse: number | null;
  effectif: number;
}

export interface Bulletin {
  inscriptionId: number;
  matricule: string;
  nom: string;
  prenom: string;
  redoublant: boolean;
  classeNom: string;
  anneeScolaire: string;
  trimestreNumero: number;
  avecComposition: boolean;
  poidsComposition: number;
  lignes: LigneBulletin[];
  totalCoefficients: number | null;
  totalPoints: number | null;
  moyenneGenerale: number | null;
  rang: number | null;
  exAequo: boolean;
  mention: string | null;
  appreciationConseil: string | null;
  bloqueImpaye: boolean;
  statistiques: StatistiquesClasse;
  observations: string[];
}

export interface LigneClassement {
  inscriptionId: number;
  matricule: string;
  nom: string;
  prenom: string;
  moyenneGenerale: number | null;
  rang: number | null;
  exAequo: boolean;
  mention: string | null;
  bloqueImpaye: boolean;
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

  creerTrimestre(
    anneeId: number,
    t: { numero: number; dateDebut: string; dateFin: string },
  ): Observable<Trimestre> {
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
    return this.http.put<Trimestre>(
      `${this.base}/trimestres/${trimestreId}/composition/${cycle}`,
      reglage,
    );
  }

  supprimerReglageCycle(trimestreId: number, cycle: Cycle): Observable<Trimestre> {
    return this.http.delete<Trimestre>(
      `${this.base}/trimestres/${trimestreId}/composition/${cycle}`,
    );
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

  creerNiveau(n: {
    code: string;
    libelle: string;
    cycle: Cycle;
    ordre: number;
  }): Observable<Niveau> {
    return this.http.post<Niveau>(`${this.base}/niveaux`, n);
  }

  classes(anneeId: number): Observable<Classe[]> {
    return this.http.get<Classe[]>(`${this.base}/annees-scolaires/${anneeId}/classes`);
  }

  creerClasse(
    anneeId: number,
    c: { niveauId: number; nom: string; effectifMax: number },
  ): Observable<Classe> {
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
    return this.http.post<Personnel>(
      `${this.base}/personnel/${id}/${actif ? 'activation' : 'desactivation'}`,
      null,
    );
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

  corrigerCategorie(
    inscriptionId: number,
    categorie: string,
    motif: string,
  ): Observable<Inscription> {
    return this.http.put<Inscription>(`${this.base}/inscriptions/${inscriptionId}/categorie`, {
      categorie,
      motif,
    });
  }

  // ---------- Imports Excel / CSV ----------

  // ---------- Finances : paramétrage (SF-21, SF-22, SF-26) ----------

  typesFrais(actifsSeulement = false): Observable<TypeFrais[]> {
    return this.http.get<TypeFrais[]>(
      `${this.base}/types-frais?actifsSeulement=${actifsSeulement}`,
    );
  }

  creerTypeFrais(saisie: Omit<TypeFrais, 'id' | 'actif'>): Observable<TypeFrais> {
    return this.http.post<TypeFrais>(`${this.base}/types-frais`, saisie);
  }

  modifierTypeFrais(id: number, saisie: Omit<TypeFrais, 'id' | 'actif'>): Observable<TypeFrais> {
    return this.http.put<TypeFrais>(`${this.base}/types-frais/${id}`, saisie);
  }

  activerTypeFrais(id: number, actif: boolean): Observable<TypeFrais> {
    return this.http.post<TypeFrais>(
      `${this.base}/types-frais/${id}/activation?actif=${actif}`,
      null,
    );
  }

  grilleTarifaire(anneeId: number): Observable<Tarif[]> {
    return this.http.get<Tarif[]>(`${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`);
  }

  ajouterTarif(
    anneeId: number,
    saisie: Omit<Tarif, 'id' | 'typeFraisCode' | 'typeFraisLibelle' | 'niveauCode' | 'classeNom'>,
  ): Observable<Tarif> {
    return this.http.post<Tarif>(
      `${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`,
      saisie,
    );
  }

  supprimerTarif(tarifId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/grille-tarifaire/${tarifId}`);
  }

  viderGrille(anneeId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`);
  }

  dupliquerGrille(
    anneeId: number,
    anneeSourceId: number,
    haussePourcentage: number,
  ): Observable<Tarif[]> {
    return this.http.post<Tarif[]>(
      `${this.base}/annees-scolaires/${anneeId}/grille-tarifaire/duplication`,
      {
        anneeSourceId,
        haussePourcentage,
      },
    );
  }

  plansPaiement(anneeId: number): Observable<PlanPaiement[]> {
    return this.http.get<PlanPaiement[]>(`${this.base}/annees-scolaires/${anneeId}/plans-paiement`);
  }

  creerPlan(anneeId: number, saisie: unknown): Observable<PlanPaiement> {
    return this.http.post<PlanPaiement>(
      `${this.base}/annees-scolaires/${anneeId}/plans-paiement`,
      saisie,
    );
  }

  modifierPlan(planId: number, saisie: unknown): Observable<PlanPaiement> {
    return this.http.put<PlanPaiement>(`${this.base}/plans-paiement/${planId}`, saisie);
  }

  supprimerPlan(planId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/plans-paiement/${planId}`);
  }

  // ---------- Finances : fiche de l'élève et caisse (SF-24 à SF-32) ----------

  ficheFinanciere(inscriptionId: number): Observable<FicheFinanciere> {
    return this.http.get<FicheFinanciere>(`${this.base}/inscriptions/${inscriptionId}/finances`);
  }

  genererFrais(inscriptionId: number): Observable<string[]> {
    return this.http.post<string[]>(
      `${this.base}/inscriptions/${inscriptionId}/finances/generation`,
      null,
    );
  }

  ajouterFraisFacultatif(inscriptionId: number, typeFraisId: number): Observable<FraisEleve> {
    return this.http.post<FraisEleve>(`${this.base}/inscriptions/${inscriptionId}/finances/frais`, {
      typeFraisId,
    });
  }

  appliquerRemise(
    fraisId: number,
    remise: { type: TypeRemise | null; valeur: number | null; motif: string | null },
  ): Observable<FraisEleve> {
    return this.http.put<FraisEleve>(`${this.base}/frais-eleve/${fraisId}/remise`, remise);
  }

  supprimerFrais(fraisId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/frais-eleve/${fraisId}`);
  }

  encaisser(
    inscriptionId: number,
    saisie: {
      datePaiement: string;
      montant: number;
      mode: ModePaiement;
      reference: string | null;
      payeur: string | null;
      typeFraisId: number | null;
    },
  ): Observable<Paiement> {
    return this.http.post<Paiement>(`${this.base}/inscriptions/${inscriptionId}/paiements`, saisie);
  }

  annulerPaiement(paiementId: number, motif: string): Observable<Paiement> {
    return this.http.post<Paiement>(`${this.base}/paiements/${paiementId}/annulation`, { motif });
  }

  recu(paiementId: number, duplicata = false): Observable<Recu> {
    return this.http.get<Recu>(`${this.base}/paiements/${paiementId}/recu?duplicata=${duplicata}`);
  }

  impayes(anneeId: number, classeId: number | null): Observable<Impaye[]> {
    const filtre = classeId ? `?classeId=${classeId}` : '';
    return this.http.get<Impaye[]>(`${this.base}/annees-scolaires/${anneeId}/impayes${filtre}`);
  }

  journalCaisse(journee: string): Observable<JournalCaisse> {
    return this.http.get<JournalCaisse>(`${this.base}/caisse/journal?journee=${journee}`);
  }

  recouvrement(anneeId: number): Observable<Recouvrement> {
    return this.http.get<Recouvrement>(`${this.base}/annees-scolaires/${anneeId}/recouvrement`);
  }

  // ---------- Notes et bulletins (SF-09 à SF-18) ----------

  matieres(): Observable<Matiere[]> {
    return this.http.get<Matiere[]>(`${this.base}/matieres`);
  }

  creerMatiere(saisie: { code: string; libelle: string }): Observable<Matiere> {
    return this.http.post<Matiere>(`${this.base}/matieres`, saisie);
  }

  enseignements(classeId: number): Observable<Enseignement[]> {
    return this.http.get<Enseignement[]>(`${this.base}/classes/${classeId}/enseignements`);
  }

  ajouterEnseignement(
    classeId: number,
    saisie: { matiereId: number; enseignantId: number | null; coefficient: number },
  ): Observable<Enseignement> {
    return this.http.post<Enseignement>(`${this.base}/classes/${classeId}/enseignements`, saisie);
  }

  modifierEnseignement(
    enseignementId: number,
    saisie: { matiereId: number; enseignantId: number | null; coefficient: number },
  ): Observable<Enseignement> {
    return this.http.put<Enseignement>(`${this.base}/enseignements/${enseignementId}`, saisie);
  }

  supprimerEnseignement(enseignementId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/enseignements/${enseignementId}`);
  }

  evaluations(enseignementId: number, trimestreId: number): Observable<Evaluation[]> {
    return this.http.get<Evaluation[]>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/evaluations`,
    );
  }

  creerEvaluation(
    enseignementId: number,
    trimestreId: number,
    saisie: { categorie: CategorieEvaluation; libelle: string; dateEval: string | null },
  ): Observable<Evaluation> {
    return this.http.post<Evaluation>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/evaluations`,
      saisie,
    );
  }

  supprimerEvaluation(evaluationId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/evaluations/${evaluationId}`);
  }

  grille(enseignementId: number, trimestreId: number): Observable<Grille> {
    return this.http.get<Grille>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/grille`,
    );
  }

  enregistrerGrille(
    enseignementId: number,
    trimestreId: number,
    notes: {
      inscriptionId: number;
      evaluationId: number;
      valeur: number | null;
      absent: boolean;
      appreciation: string | null;
    }[],
  ): Observable<Grille> {
    return this.http.put<Grille>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/grille`,
      { notes },
    );
  }

  classement(classeId: number, trimestreId: number): Observable<LigneClassement[]> {
    return this.http.get<LigneClassement[]>(
      `${this.base}/classes/${classeId}/trimestres/${trimestreId}/classement`,
    );
  }

  deliberer(classeId: number, trimestreId: number): Observable<LigneClassement[]> {
    return this.http.post<LigneClassement[]>(
      `${this.base}/classes/${classeId}/trimestres/${trimestreId}/deliberation`,
      null,
    );
  }

  bulletin(inscriptionId: number, trimestreId: number): Observable<Bulletin> {
    return this.http.get<Bulletin>(
      `${this.base}/inscriptions/${inscriptionId}/trimestres/${trimestreId}/bulletin`,
    );
  }

  enregistrerDecision(
    inscriptionId: number,
    trimestreId: number,
    decision: { mention: string | null; appreciationConseil: string | null },
  ): Observable<Bulletin> {
    return this.http.put<Bulletin>(
      `${this.base}/inscriptions/${inscriptionId}/trimestres/${trimestreId}/decision`,
      decision,
    );
  }

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
