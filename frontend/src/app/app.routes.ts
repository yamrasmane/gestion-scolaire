import { Routes } from '@angular/router';
import { roleGuard } from './core/role.guard';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/accueil').then((m) => m.Accueil), title: 'Accueil' },
  {
    path: 'annees-scolaires',
    loadComponent: () => import('./pages/annees-scolaires').then((m) => m.AnneesScolaires),
    title: 'Années scolaires',
  },
  {
    path: 'trimestres',
    loadComponent: () => import('./pages/trimestres').then((m) => m.Trimestres),
    title: 'Trimestres',
  },
  { path: 'classes', loadComponent: () => import('./pages/classes').then((m) => m.Classes), title: 'Classes' },
  { path: 'eleves', loadComponent: () => import('./pages/eleves').then((m) => m.Eleves), title: 'Élèves' },
  {
    path: 'personnel',
    loadComponent: () => import('./pages/personnel').then((m) => m.Personnel),
    canActivate: [roleGuard('DIRECTEUR', 'CENSEUR')],
    title: 'Personnel',
  },
  {
    path: 'acces-refuse',
    loadComponent: () => import('./pages/acces-refuse').then((m) => m.AccesRefuse),
    title: 'Accès refusé',
  },
  { path: '**', redirectTo: '' },
];
