import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { config } from './config';

/** Une page de résultats renvoyée par l'API (`PageVue` côté serveur). */
export interface Page<T> {
  contenu: T[];
  page: number;
  taille: number;
  total: number;
  pages: number;
}

/** Page et tri demandés à l'API. `tri` suit la forme Spring : « nom,asc ». */
export interface Pagination {
  page?: number;
  taille?: number;
  tri?: string | null;
}

/** Construit la chaîne de requête : les critères vides ne sont pas envoyés. */
export function parametres(criteres: object = {}): HttpParams {
  let params = new HttpParams();
  for (const [cle, valeur] of Object.entries(criteres as Record<string, unknown>)) {
    if (valeur === null || valeur === undefined || valeur === '') {
      continue;
    }
    const nom = cle === 'taille' ? 'size' : cle === 'tri' ? 'sort' : cle;
    params = params.set(nom, String(valeur));
  }
  return params;
}

export interface Profil {
  identifiant: string;
  nomUtilisateur: string;
  nomComplet: string | null;
  email: string | null;
  roles: string[];
}

export interface AnneeScolaire {
  id: string;
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
  id: string;
  numero: number;
  dateDebut: string;
  dateFin: string;
  avecComposition: boolean | null;
  poidsComposition: number | null;
  cloture: boolean;
  compositionParCycle: Record<Cycle, CompositionEffective>;
}

export interface Niveau {
  id: string;
  code: string;
  libelle: string;
  cycle: Cycle;
  ordre: number;
}

export interface Classe {
  id: string;
  niveauId: string;
  niveauCode: string;
  cycle: Cycle;
  nom: string;
  effectifMax: number;
  effectif: number;
}

export interface Personnel {
  id: string;
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
  id: string;
  matricule: string;
  nom: string;
  prenom: string;
  dateNaissance: string;
  sexe: 'M' | 'F';
  nomParent: string | null;
  contactParent: string | null;
}

export interface Inscription {
  id: string;
  eleve: Eleve;
  classeId: string;
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
  id: string;
  code: string;
  libelle: string;
  obligatoire: boolean;
  fractionnable: boolean;
  prioriteImputation: number;
  actif: boolean;
}

export interface Tarif {
  id: string;
  typeFraisId: string;
  typeFraisCode: string;
  typeFraisLibelle: string;
  niveauId: string | null;
  niveauCode: string | null;
  classeId: string | null;
  classeNom: string | null;
  categorie: CategorieTarif;
  montant: number;
}

export interface Tranche {
  id: string;
  numero: number;
  libelle: string;
  dateEcheance: string;
  pourcentage: number | null;
  montant: number | null;
}

export interface PlanPaiement {
  id: string;
  libelle: string;
  niveauId: string | null;
  niveauCode: string | null;
  classeId: string | null;
  classeNom: string | null;
  categorie: 'NOUVEAU' | 'ANCIEN' | null;
  modeRepartition: ModeRepartition;
  parDefaut: boolean;
  montantMinInscription: number | null;
  tranches: Tranche[];
}

export interface FraisEleve {
  id: string;
  typeFraisId: string;
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
  id: string;
  fraisEleveId: string;
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
  echeanceId: string;
  typeFraisLibelle: string;
  trancheLibelle: string;
  montant: number;
}

export interface Paiement {
  id: string;
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
  inscriptionId: string;
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
  inscriptionId: string;
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
  /** Le total et les totaux par mode portent sur la journée, pas sur la page. */
  paiements: Page<Paiement>;
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
  id: string;
  code: string;
  libelle: string;
}

export interface Enseignement {
  id: string;
  classeId: string;
  classeNom: string;
  matiereId: string;
  matiereCode: string;
  matiereLibelle: string;
  enseignantId: string | null;
  enseignantNom: string | null;
  coefficient: number;
}

export interface LigneProgramme {
  id: string;
  niveauId: string;
  niveauCode: string;
  niveauLibelle: string;
  matiereId: string;
  matiereCode: string;
  matiereLibelle: string;
  coefficient: number;
}

export interface ApplicationProgramme {
  classesTouchees: number;
  matieresAjoutees: number;
  coefficientsMisAJour: number;
  details: string[];
}

export interface ChargeEnseignant {
  enseignantId: string;
  enseignantNom: string;
  nombreClasses: number;
  totalCoefficients: number;
  enseignements: Enseignement[];
}

export interface Evaluation {
  id: string;
  enseignementId: string;
  trimestreId: string;
  categorie: CategorieEvaluation;
  libelle: string;
  dateEval: string | null;
  bareme: number;
}

export interface NoteVue {
  inscriptionId: string;
  evaluationId: string;
  valeur: number | null;
  absent: boolean;
  appreciation: string | null;
}

export interface LigneGrille {
  inscriptionId: string;
  matricule: string;
  nom: string;
  prenom: string;
  notes: NoteVue[];
  moyenne: number | null;
  mention: string | null;
}

export interface Grille {
  enseignementId: string;
  matiereLibelle: string;
  classeNom: string;
  trimestreId: string;
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
  inscriptionId: string;
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
  inscriptionId: string;
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

  activerAnneeScolaire(id: string): Observable<AnneeScolaire> {
    return this.http.post<AnneeScolaire>(`${this.base}/annees-scolaires/${id}/activation`, null);
  }

  // ---------- Trimestres et composition ----------

  trimestres(anneeId: string): Observable<Trimestre[]> {
    return this.http.get<Trimestre[]>(`${this.base}/annees-scolaires/${anneeId}/trimestres`);
  }

  creerTrimestre(
    anneeId: string,
    t: { numero: number; dateDebut: string; dateFin: string },
  ): Observable<Trimestre> {
    return this.http.post<Trimestre>(`${this.base}/annees-scolaires/${anneeId}/trimestres`, t);
  }

  reglerComposition(
    trimestreId: string,
    reglage: { avecComposition: boolean | null; poidsComposition: number | null },
  ): Observable<Trimestre> {
    return this.http.put<Trimestre>(`${this.base}/trimestres/${trimestreId}/composition`, reglage);
  }

  reglerCompositionCycle(
    trimestreId: string,
    cycle: Cycle,
    reglage: { avecComposition: boolean; poidsComposition: number },
  ): Observable<Trimestre> {
    return this.http.put<Trimestre>(
      `${this.base}/trimestres/${trimestreId}/composition/${cycle}`,
      reglage,
    );
  }

  supprimerReglageCycle(trimestreId: string, cycle: Cycle): Observable<Trimestre> {
    return this.http.delete<Trimestre>(
      `${this.base}/trimestres/${trimestreId}/composition/${cycle}`,
    );
  }

  cloturerTrimestre(trimestreId: string): Observable<Trimestre> {
    return this.http.post<Trimestre>(`${this.base}/trimestres/${trimestreId}/cloture`, null);
  }

  rouvrirTrimestre(trimestreId: string): Observable<Trimestre> {
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

  classes(anneeId: string): Observable<Classe[]> {
    return this.http.get<Classe[]>(`${this.base}/annees-scolaires/${anneeId}/classes`);
  }

  creerClasse(
    anneeId: string,
    c: { niveauId: string; nom: string; effectifMax: number },
  ): Observable<Classe> {
    return this.http.post<Classe>(`${this.base}/annees-scolaires/${anneeId}/classes`, c);
  }

  // ---------- Personnel ----------

  personnel(
    criteres: Pagination & {
      recherche?: string;
      role?: string | null;
      actif?: boolean | null;
    } = {},
  ): Observable<Page<Personnel>> {
    return this.http.get<Page<Personnel>>(`${this.base}/personnel`, {
      params: parametres(criteres),
    });
  }

  /** Liste complète, pour alimenter une liste déroulante. */
  tousLesPersonnels(): Observable<Personnel[]> {
    return this.personnel({ taille: 500 }).pipe(map((page) => page.contenu));
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

  modifierPersonnel(
    id: string,
    p: { nom: string; prenom: string; email: string; telephone: string; role: string },
  ): Observable<Personnel> {
    return this.http.put<Personnel>(`${this.base}/personnel/${id}`, p);
  }

  chargeEnseignant(id: string): Observable<ChargeEnseignant> {
    return this.http.get<ChargeEnseignant>(`${this.base}/personnel/${id}/charge`);
  }

  activerPersonnel(id: string, actif: boolean): Observable<Personnel> {
    return this.http.post<Personnel>(
      `${this.base}/personnel/${id}/${actif ? 'activation' : 'desactivation'}`,
      null,
    );
  }

  // ---------- Élèves et inscriptions ----------

  inscriptions(
    classeId: string,
    criteres: Pagination & {
      recherche?: string;
      statut?: string | null;
      redoublant?: boolean | null;
    } = {},
  ): Observable<Page<Inscription>> {
    return this.http.get<Page<Inscription>>(`${this.base}/classes/${classeId}/inscriptions`, {
      params: parametres(criteres),
    });
  }

  /** Inscriptions complètes d'une classe, pour alimenter une liste déroulante. */
  toutesLesInscriptions(classeId: string): Observable<Inscription[]> {
    return this.inscriptions(classeId, { taille: 500 }).pipe(map((page) => page.contenu));
  }

  /** Liste paginée des élèves, tous rattachements confondus. */
  eleves(
    criteres: Pagination & {
      recherche?: string;
      sexe?: string | null;
      neApres?: string | null;
      neAvant?: string | null;
    } = {},
  ): Observable<Page<Eleve>> {
    return this.http.get<Page<Eleve>>(`${this.base}/eleves`, { params: parametres(criteres) });
  }

  inscrire(
    classeId: string,
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
    inscriptionId: string,
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

  modifierTypeFrais(id: string, saisie: Omit<TypeFrais, 'id' | 'actif'>): Observable<TypeFrais> {
    return this.http.put<TypeFrais>(`${this.base}/types-frais/${id}`, saisie);
  }

  activerTypeFrais(id: string, actif: boolean): Observable<TypeFrais> {
    return this.http.post<TypeFrais>(
      `${this.base}/types-frais/${id}/activation?actif=${actif}`,
      null,
    );
  }

  grilleTarifaire(anneeId: string): Observable<Tarif[]> {
    return this.http.get<Tarif[]>(`${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`);
  }

  ajouterTarif(
    anneeId: string,
    saisie: Omit<Tarif, 'id' | 'typeFraisCode' | 'typeFraisLibelle' | 'niveauCode' | 'classeNom'>,
  ): Observable<Tarif> {
    return this.http.post<Tarif>(
      `${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`,
      saisie,
    );
  }

  supprimerTarif(tarifId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/grille-tarifaire/${tarifId}`);
  }

  viderGrille(anneeId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/annees-scolaires/${anneeId}/grille-tarifaire`);
  }

  dupliquerGrille(
    anneeId: string,
    anneeSourceId: string,
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

  plansPaiement(anneeId: string): Observable<PlanPaiement[]> {
    return this.http.get<PlanPaiement[]>(`${this.base}/annees-scolaires/${anneeId}/plans-paiement`);
  }

  creerPlan(anneeId: string, saisie: unknown): Observable<PlanPaiement> {
    return this.http.post<PlanPaiement>(
      `${this.base}/annees-scolaires/${anneeId}/plans-paiement`,
      saisie,
    );
  }

  modifierPlan(planId: string, saisie: unknown): Observable<PlanPaiement> {
    return this.http.put<PlanPaiement>(`${this.base}/plans-paiement/${planId}`, saisie);
  }

  supprimerPlan(planId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/plans-paiement/${planId}`);
  }

  // ---------- Finances : fiche de l'élève et caisse (SF-24 à SF-32) ----------

  ficheFinanciere(inscriptionId: string): Observable<FicheFinanciere> {
    return this.http.get<FicheFinanciere>(`${this.base}/inscriptions/${inscriptionId}/finances`);
  }

  genererFrais(inscriptionId: string): Observable<string[]> {
    return this.http.post<string[]>(
      `${this.base}/inscriptions/${inscriptionId}/finances/generation`,
      null,
    );
  }

  ajouterFraisFacultatif(inscriptionId: string, typeFraisId: string): Observable<FraisEleve> {
    return this.http.post<FraisEleve>(`${this.base}/inscriptions/${inscriptionId}/finances/frais`, {
      typeFraisId,
    });
  }

  appliquerRemise(
    fraisId: string,
    remise: { type: TypeRemise | null; valeur: number | null; motif: string | null },
  ): Observable<FraisEleve> {
    return this.http.put<FraisEleve>(`${this.base}/frais-eleve/${fraisId}/remise`, remise);
  }

  supprimerFrais(fraisId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/frais-eleve/${fraisId}`);
  }

  encaisser(
    inscriptionId: string,
    saisie: {
      datePaiement: string;
      montant: number;
      mode: ModePaiement;
      reference: string | null;
      payeur: string | null;
      typeFraisId: string | null;
    },
  ): Observable<Paiement> {
    return this.http.post<Paiement>(`${this.base}/inscriptions/${inscriptionId}/paiements`, saisie);
  }

  annulerPaiement(paiementId: string, motif: string): Observable<Paiement> {
    return this.http.post<Paiement>(`${this.base}/paiements/${paiementId}/annulation`, { motif });
  }

  recu(paiementId: string, duplicata = false): Observable<Recu> {
    return this.http.get<Recu>(`${this.base}/paiements/${paiementId}/recu?duplicata=${duplicata}`);
  }

  impayes(
    anneeId: string,
    criteres: Pagination & {
      classeId?: string | null;
      limite?: string | null;
      recherche?: string;
    } = {},
  ): Observable<Page<Impaye>> {
    return this.http.get<Page<Impaye>>(`${this.base}/annees-scolaires/${anneeId}/impayes`, {
      params: parametres(criteres),
    });
  }

  journalCaisse(journee: string, pagination: Pagination = {}): Observable<JournalCaisse> {
    return this.http.get<JournalCaisse>(`${this.base}/caisse/journal`, {
      params: parametres({ journee, ...pagination }),
    });
  }

  recouvrement(anneeId: string): Observable<Recouvrement> {
    return this.http.get<Recouvrement>(`${this.base}/annees-scolaires/${anneeId}/recouvrement`);
  }

  // ---------- Notes et bulletins (SF-09 à SF-18) ----------

  matieres(): Observable<Matiere[]> {
    return this.http.get<Matiere[]>(`${this.base}/matieres`);
  }

  creerMatiere(saisie: { code: string; libelle: string }): Observable<Matiere> {
    return this.http.post<Matiere>(`${this.base}/matieres`, saisie);
  }

  modifierMatiere(id: string, saisie: { code: string; libelle: string }): Observable<Matiere> {
    return this.http.put<Matiere>(`${this.base}/matieres/${id}`, saisie);
  }

  supprimerMatiere(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/matieres/${id}`);
  }

  programme(niveauId: string): Observable<LigneProgramme[]> {
    return this.http.get<LigneProgramme[]>(`${this.base}/niveaux/${niveauId}/programme`);
  }

  ajouterAuProgramme(
    niveauId: string,
    saisie: { matiereId: string; coefficient: number },
  ): Observable<LigneProgramme> {
    return this.http.post<LigneProgramme>(`${this.base}/niveaux/${niveauId}/programme`, saisie);
  }

  modifierLigneProgramme(
    ligneId: string,
    saisie: { matiereId: string; coefficient: number },
  ): Observable<LigneProgramme> {
    return this.http.put<LigneProgramme>(`${this.base}/programme/${ligneId}`, saisie);
  }

  retirerDuProgramme(ligneId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/programme/${ligneId}`);
  }

  appliquerProgramme(
    niveauId: string,
    anneeId: string,
    alignerCoefficients: boolean,
  ): Observable<ApplicationProgramme> {
    return this.http.post<ApplicationProgramme>(
      `${this.base}/niveaux/${niveauId}/programme/application` +
        `?anneeId=${anneeId}&alignerCoefficients=${alignerCoefficients}`,
      null,
    );
  }

  affecter(
    classeId: string,
    affectations: { enseignementId: string; enseignantId: string | null }[],
  ): Observable<Enseignement[]> {
    return this.http.put<Enseignement[]>(`${this.base}/classes/${classeId}/affectations`, {
      affectations,
    });
  }

  enseignements(classeId: string): Observable<Enseignement[]> {
    return this.http.get<Enseignement[]>(`${this.base}/classes/${classeId}/enseignements`);
  }

  ajouterEnseignement(
    classeId: string,
    saisie: { matiereId: string; enseignantId: string | null; coefficient: number },
  ): Observable<Enseignement> {
    return this.http.post<Enseignement>(`${this.base}/classes/${classeId}/enseignements`, saisie);
  }

  modifierEnseignement(
    enseignementId: string,
    saisie: { matiereId: string; enseignantId: string | null; coefficient: number },
  ): Observable<Enseignement> {
    return this.http.put<Enseignement>(`${this.base}/enseignements/${enseignementId}`, saisie);
  }

  supprimerEnseignement(enseignementId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/enseignements/${enseignementId}`);
  }

  evaluations(enseignementId: string, trimestreId: string): Observable<Evaluation[]> {
    return this.http.get<Evaluation[]>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/evaluations`,
    );
  }

  creerEvaluation(
    enseignementId: string,
    trimestreId: string,
    saisie: { categorie: CategorieEvaluation; libelle: string; dateEval: string | null },
  ): Observable<Evaluation> {
    return this.http.post<Evaluation>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/evaluations`,
      saisie,
    );
  }

  supprimerEvaluation(evaluationId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/evaluations/${evaluationId}`);
  }

  grille(enseignementId: string, trimestreId: string): Observable<Grille> {
    return this.http.get<Grille>(
      `${this.base}/enseignements/${enseignementId}/trimestres/${trimestreId}/grille`,
    );
  }

  enregistrerGrille(
    enseignementId: string,
    trimestreId: string,
    notes: {
      inscriptionId: string;
      evaluationId: string;
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

  classement(classeId: string, trimestreId: string): Observable<LigneClassement[]> {
    return this.http.get<LigneClassement[]>(
      `${this.base}/classes/${classeId}/trimestres/${trimestreId}/classement`,
    );
  }

  deliberer(classeId: string, trimestreId: string): Observable<LigneClassement[]> {
    return this.http.post<LigneClassement[]>(
      `${this.base}/classes/${classeId}/trimestres/${trimestreId}/deliberation`,
      null,
    );
  }

  bulletin(inscriptionId: string, trimestreId: string): Observable<Bulletin> {
    return this.http.get<Bulletin>(
      `${this.base}/inscriptions/${inscriptionId}/trimestres/${trimestreId}/bulletin`,
    );
  }

  enregistrerDecision(
    inscriptionId: string,
    trimestreId: string,
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
