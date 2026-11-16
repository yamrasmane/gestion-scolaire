/** Rôles du realm Keycloak, identiques à ceux contrôlés par l'API. */
export type Role = 'DIRECTEUR' | 'CENSEUR' | 'ENSEIGNANT' | 'INTENDANT' | 'ECONOME' | 'SURVEILLANT';

export const DIRECTION: Role[] = ['DIRECTEUR', 'CENSEUR'];
export const SCOLARITE: Role[] = ['DIRECTEUR', 'CENSEUR', 'SURVEILLANT'];
export const FINANCES: Role[] = ['DIRECTEUR', 'CENSEUR', 'INTENDANT', 'ECONOME'];

export const LIBELLES_ROLES: Record<Role, string> = {
  DIRECTEUR: 'Directeur',
  CENSEUR: 'Censeur',
  ENSEIGNANT: 'Enseignant',
  INTENDANT: 'Intendant',
  ECONOME: 'Économe',
  SURVEILLANT: 'Surveillant',
};
