import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AnneeScolaire, ApiService, Profil, messageErreur } from '@core/api.service';
import { AuthService } from '@core/authentication';
import { PageHeader } from '@shared/components/page-header/page-header';

@Component({
  selector: 'app-accueil',
  template: `
    <page-header [title]="'Bonjour ' + auth.nomComplet()" subtitle="Tableau de bord" />

    <div class="row">
      <div class="col-md-4">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Votre compte</mat-card-title>
            <mat-card-subtitle>{{ auth.nomUtilisateur() }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <mat-chip-set>
              @for (role of auth.libellesRoles(); track role) {
                <mat-chip>{{ role }}</mat-chip>
              } @empty {
                <span class="text-muted"> Aucun rôle attribué — contactez l'administrateur. </span>
              }
            </mat-chip-set>
          </mat-card-content>
        </mat-card>
      </div>

      <div class="col-md-4">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Connexion à l'API</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (erreur(); as message) {
              <p class="alerte-erreur">{{ message }}</p>
            } @else if (profil(); as p) {
              <p>
                L'API vous reconnaît comme <strong>{{ p.nomUtilisateur }}</strong> ({{
                  p.roles.join(', ') || 'aucun rôle'
                }}).
              </p>
            } @else {
              <p class="text-muted">Vérification en cours…</p>
            }
          </mat-card-content>
        </mat-card>
      </div>

      <div class="col-md-4">
        <mat-card>
          <mat-card-header>
            <mat-card-title>Année scolaire active</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            @if (anneeActive(); as annee) {
              <p class="f-s-20 f-w-500">{{ annee.libelle }}</p>
            } @else {
              <p class="text-muted">Aucune année scolaire n'est active.</p>
            }
          </mat-card-content>
          <mat-card-actions>
            <a matButton routerLink="/parametrage/annees-scolaires">
              <mat-icon>event</mat-icon>
              Gérer les années
            </a>
          </mat-card-actions>
        </mat-card>
      </div>
    </div>
  `,
  imports: [RouterLink, MatCardModule, MatButtonModule, MatIconModule, MatChipsModule, PageHeader],
})
export class Accueil {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  protected readonly profil = signal<Profil | null>(null);
  protected readonly anneeActive = signal<AnneeScolaire | null>(null);
  protected readonly erreur = signal('');

  constructor() {
    this.api.profil().subscribe({
      next: (profil) => this.profil.set(profil),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
    this.api.anneesScolaires().subscribe({
      next: (annees) => this.anneeActive.set(annees.find((a) => a.actif) ?? null),
      error: () => undefined,
    });
  }
}
