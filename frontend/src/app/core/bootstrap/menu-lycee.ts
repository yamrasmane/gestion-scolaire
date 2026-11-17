import { DIRECTION, FINANCES } from '@core/roles';
import { Menu } from './menu.service';

/**
 * Menu latéral de l'application. Les libellés sont traduits
 * (`public/i18n/fr-FR.json`, section `menu`) et chaque entrée peut être
 * réservée à certains rôles Keycloak via `permissions.only`.
 */
export const MENU_LYCEE: Menu[] = [
  {
    route: 'accueil',
    name: 'accueil',
    type: 'link',
    icon: 'home',
  },
  {
    route: 'scolarite',
    name: 'scolarite',
    type: 'sub',
    icon: 'school',
    children: [
      { route: 'eleves', name: 'eleves', type: 'link' },
      { route: 'classes', name: 'classes', type: 'link' },
    ],
  },
  {
    route: 'finances',
    name: 'finances',
    type: 'sub',
    icon: 'payments',
    permissions: { only: FINANCES },
    children: [
      { route: 'encaissement', name: 'encaissement', type: 'link' },
      { route: 'impayes', name: 'impayes', type: 'link' },
      { route: 'journal-caisse', name: 'journal', type: 'link' },
    ],
  },
  {
    route: 'parametrage',
    name: 'parametrage',
    type: 'sub',
    icon: 'settings',
    children: [
      { route: 'annees-scolaires', name: 'annees', type: 'link' },
      { route: 'trimestres', name: 'trimestres', type: 'link' },
      {
        route: 'finances/types-frais',
        name: 'types_frais',
        type: 'link',
        permissions: { only: ['INTENDANT', ...DIRECTION] },
      },
      {
        route: 'finances/grille-tarifaire',
        name: 'grille',
        type: 'link',
        permissions: { only: ['INTENDANT', ...DIRECTION] },
      },
      {
        route: 'finances/plans-paiement',
        name: 'plans',
        type: 'link',
        permissions: { only: ['INTENDANT', ...DIRECTION] },
      },
    ],
  },
  {
    route: 'personnel',
    name: 'personnel',
    type: 'link',
    icon: 'badge',
    permissions: { only: DIRECTION },
  },
];
