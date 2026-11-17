import { DIRECTION } from '@core/roles';
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
    route: 'parametrage',
    name: 'parametrage',
    type: 'sub',
    icon: 'settings',
    children: [
      { route: 'annees-scolaires', name: 'annees', type: 'link' },
      { route: 'trimestres', name: 'trimestres', type: 'link' },
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
