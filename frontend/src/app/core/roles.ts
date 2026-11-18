/** Rôles du realm Keycloak, identiques à ceux contrôlés par l'API. */
export type Role =
  | 'ADMIN'
  | 'DIRECTEUR'
  | 'CENSEUR'
  | 'ENSEIGNANT'
  | 'INTENDANT'
  | 'ECONOME'
  | 'SURVEILLANT';

/** Tous les rôles connus : ce que l'on accorde à l'administrateur. */
export const TOUS_LES_ROLES: Role[] = [
  'ADMIN',
  'DIRECTEUR',
  'CENSEUR',
  'ENSEIGNANT',
  'INTENDANT',
  'ECONOME',
  'SURVEILLANT',
];

/** L'administrateur ouvre toutes les portes : il figure dans chaque liste. */
export const DIRECTION: Role[] = ['ADMIN', 'DIRECTEUR', 'CENSEUR'];
export const SCOLARITE: Role[] = ['ADMIN', 'DIRECTEUR', 'CENSEUR', 'SURVEILLANT'];
export const FINANCES: Role[] = ['ADMIN', 'DIRECTEUR', 'CENSEUR', 'INTENDANT', 'ECONOME'];
export const ENSEIGNEMENT: Role[] = ['ADMIN', 'DIRECTEUR', 'CENSEUR', 'ENSEIGNANT'];

export const LIBELLES_ROLES: Record<Role, string> = {
  ADMIN: 'Administrateur',
  DIRECTEUR: 'Directeur',
  CENSEUR: 'Censeur',
  ENSEIGNANT: 'Enseignant',
  INTENDANT: 'Intendant',
  ECONOME: 'Économe',
  SURVEILLANT: 'Surveillant',
};
