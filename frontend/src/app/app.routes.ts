import { Routes } from '@angular/router';

import { authGuard, roleGuard } from '@core';
import { DIRECTION, FINANCES } from '@core/roles';
import { AdminLayout } from '@theme/admin-layout/admin-layout';
import { ConnexionImpossible } from './routes/sessions/connexion-impossible';
import { Error403 } from './routes/sessions/error-403';
import { Error404 } from './routes/sessions/error-404';
import { Error500 } from './routes/sessions/error-500';

export const routes: Routes = [
  { path: 'connexion-impossible', component: ConnexionImpossible },
  {
    // Bulletin imprimable, hors coquille d'administration (SF-17).
    path: 'bulletin/:inscriptionId/:trimestreId',
    canActivate: [authGuard],
    loadComponent: () => import('./routes/notes/bulletin').then((m) => m.Bulletin),
  },
  {
    // Reçu imprimable, hors coquille d'administration (SF-30).
    path: 'recu/:paiementId',
    canActivate: [authGuard],
    loadComponent: () => import('./routes/finances/recu').then((m) => m.Recu),
  },
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
        path: 'notes',
        children: [
          { path: '', redirectTo: 'saisie', pathMatch: 'full' },
          {
            path: 'saisie',
            loadComponent: () => import('./routes/notes/saisie-notes').then((m) => m.SaisieNotes),
          },
          {
            path: 'classement',
            loadComponent: () => import('./routes/notes/classement').then((m) => m.Classement),
          },
        ],
      },
      {
        path: 'parametrage/programmes',
        canActivate: [roleGuard(...DIRECTION)],
        loadComponent: () => import('./routes/parametrage/programmes').then((m) => m.Programmes),
      },
      {
        path: 'notes/affectations',
        canActivate: [roleGuard(...DIRECTION)],
        loadComponent: () => import('./routes/notes/affectations').then((m) => m.Affectations),
      },
      {
        path: 'parametrage/matieres',
        canActivate: [roleGuard(...DIRECTION)],
        loadComponent: () => import('./routes/notes/matieres').then((m) => m.Matieres),
      },
      {
        path: 'finances',
        canActivate: [roleGuard(...FINANCES)],
        children: [
          { path: '', redirectTo: 'encaissement', pathMatch: 'full' },
          {
            path: 'encaissement',
            loadComponent: () =>
              import('./routes/finances/encaissement').then((m) => m.Encaissement),
          },
          {
            path: 'impayes',
            loadComponent: () => import('./routes/finances/impayes').then((m) => m.Impayes),
          },
          {
            path: 'journal-caisse',
            loadComponent: () =>
              import('./routes/finances/journal-caisse').then((m) => m.JournalCaisse),
          },
        ],
      },
      {
        path: 'parametrage/finances',
        canActivate: [roleGuard('INTENDANT', 'DIRECTEUR', 'CENSEUR')],
        children: [
          { path: '', redirectTo: 'types-frais', pathMatch: 'full' },
          {
            path: 'types-frais',
            loadComponent: () => import('./routes/finances/types-frais').then((m) => m.TypesFrais),
          },
          {
            path: 'grille-tarifaire',
            loadComponent: () =>
              import('./routes/finances/grille-tarifaire').then((m) => m.GrilleTarifaire),
          },
          {
            path: 'plans-paiement',
            loadComponent: () =>
              import('./routes/finances/plans-paiement').then((m) => m.PlansPaiement),
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
