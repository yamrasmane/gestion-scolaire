import { Routes } from '@angular/router';

import { authGuard, roleGuard } from '@core';
import { DIRECTION } from '@core/roles';
import { AdminLayout } from '@theme/admin-layout/admin-layout';
import { Error403 } from './routes/sessions/error-403';
import { Error404 } from './routes/sessions/error-404';
import { Error500 } from './routes/sessions/error-500';

export const routes: Routes = [
  {
    path: '',
    component: AdminLayout,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', redirectTo: 'accueil', pathMatch: 'full' },
      {
        path: 'accueil',
        loadComponent: () => import('./routes/accueil/accueil').then((m) => m.Accueil),
      },
      {
        path: 'scolarite',
        children: [
          { path: '', redirectTo: 'eleves', pathMatch: 'full' },
          {
            path: 'eleves',
            loadComponent: () => import('./routes/scolarite/eleves').then((m) => m.Eleves),
          },
          {
            path: 'classes',
            loadComponent: () => import('./routes/scolarite/classes').then((m) => m.Classes),
          },
        ],
      },
      {
        path: 'parametrage',
        children: [
          { path: '', redirectTo: 'annees-scolaires', pathMatch: 'full' },
          {
            path: 'annees-scolaires',
            loadComponent: () =>
              import('./routes/parametrage/annees-scolaires').then((m) => m.AnneesScolaires),
          },
          {
            path: 'trimestres',
            loadComponent: () =>
              import('./routes/parametrage/trimestres').then((m) => m.Trimestres),
          },
        ],
      },
      {
        path: 'personnel',
        canActivate: [roleGuard(...DIRECTION)],
        loadComponent: () => import('./routes/personnel/personnel').then((m) => m.Personnel),
      },
      { path: '403', component: Error403 },
      { path: '404', component: Error404 },
      { path: '500', component: Error500 },
    ],
  },
  { path: '**', redirectTo: '404' },
];
