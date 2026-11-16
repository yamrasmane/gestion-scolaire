import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/accueil').then((m) => m.Accueil), title: 'Accueil' },
  {
    path: 'annees-scolaires',
    loadComponent: () => import('./pages/annees-scolaires').then((m) => m.AnneesScolaires),
    title: 'Années scolaires',
  },
  {
    path: 'acces-refuse',
    loadComponent: () => import('./pages/acces-refuse').then((m) => m.AccesRefuse),
    title: 'Accès refusé',
  },
  { path: '**', redirectTo: '' },
];
