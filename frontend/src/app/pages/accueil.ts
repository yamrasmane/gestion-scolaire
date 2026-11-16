import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AnneeScolaire, ApiService, Profil, messageErreur } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-accueil',
  imports: [RouterLink],
  template: `
    <h1>Bonjour {{ auth.nomComplet() }}</h1>

    <section class="carte">
      <h2>Votre compte</h2>
      <p>
        Identifiant : <strong>{{ auth.nomUtilisateur() }}</strong><br />
        Rôles :
        @for (role of auth.libellesRoles(); track role) {
          <span class="badge">{{ role }}</span>
        } @empty {
          <span class="discret">aucun rôle attribué — contactez l'administrateur</span>
        }
      </p>
    </section>

    <section class="carte">
      <h2>Connexion à l'API</h2>
      @if (erreur(); as message) {
        <p class="erreur">{{ message }}</p>
      } @else if (profil(); as p) {
        <p>
          <span class="badge succes">Connectée</span>
          L'API vous reconnaît comme <strong>{{ p.nomUtilisateur }}</strong>
          ({{ p.roles.join(', ') || 'aucun rôle' }}).
        </p>
      } @else {
        <p class="discret">Vérification en cours…</p>
      }
    </section>

    <section class="carte">
      <h2>Année scolaire active</h2>
      @if (anneeActive(); as annee) {
        <p><strong>{{ annee.libelle }}</strong></p>
      } @else {
        <p class="discret">Aucune année scolaire n'est active.</p>
      }
      <a routerLink="/annees-scolaires">Gérer les années scolaires</a>
    </section>
  `,
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
