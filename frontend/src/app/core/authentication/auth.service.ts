import { Injectable, computed, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { BehaviorSubject, Observable, from, map } from 'rxjs';

import { config } from '@core/config';
import { LIBELLES_ROLES, Role } from '@core/roles';
import { User } from './interface';

/**
 * Connexion unique (SSO) via Keycloak : OpenID Connect, flux « code » avec PKCE.
 * Remplace l'authentification par mot de passe livrée avec le gabarit : les
 * composants du gabarit (bouton utilisateur, panneau latéral, garde de route)
 * continuent d'utiliser cette façade.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = new Keycloak(config.keycloak);
  private readonly utilisateur$ = new BehaviorSubject<User>({});

  private readonly _nomComplet = signal('');
  private readonly _nomUtilisateur = signal('');
  private readonly _roles = signal<Role[]>([]);

  readonly nomComplet = this._nomComplet.asReadonly();
  readonly nomUtilisateur = this._nomUtilisateur.asReadonly();
  readonly roles = this._roles.asReadonly();
  readonly libellesRoles = computed(() => this._roles().map((role) => LIBELLES_ROLES[role]));

  /** Vrai si Keycloak n'a pas pu être joint au démarrage. */
  readonly panne = signal(false);

  /** Appelé au démarrage : redirige vers la page de connexion Keycloak si nécessaire. */
  async initialiser(): Promise<void> {
    try {
      await this.keycloak.init({
        onLoad: 'login-required',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      });
    } catch (erreur) {
      // L'application démarre quand même : sans cela, l'écran resterait
      // bloqué sur « Chargement… » sans la moindre explication.
      console.error('Connexion à Keycloak impossible', erreur);
      this.panne.set(true);
      return;
    }
    this.keycloak.onTokenExpired = () => void this.jeton();
    this.lireJeton();
  }

  /** Jeton d'accès valide, renouvelé s'il expire dans moins de 30 secondes. */
  async jeton(): Promise<string | undefined> {
    try {
      if (await this.keycloak.updateToken(30)) {
        this.lireJeton();
      }
    } catch {
      // Surtout pas de redirection vers Keycloak ici : si le serveur est
      // injoignable, chaque appel relancerait une connexion et l'adresse
      // tournerait en boucle. L'appel partira sans jeton et l'API répondra 401.
      return undefined;
    }
    return this.keycloak.token;
  }

  /** Relance une connexion, depuis la page « connexion impossible ». */
  reconnecter(): void {
    void this.keycloak.login({ redirectUri: window.location.origin });
  }

  /** Adresse du serveur Keycloak, affichée en cas de panne. */
  get adresseServeur(): string {
    return `${config.keycloak.url}/realms/${config.keycloak.realm}`;
  }

  aUnRole(...roles: Role[]): boolean {
    return roles.some((role) => this._roles().includes(role));
  }

  /** Utilisé par la garde de route du gabarit. */
  check(): boolean {
    return this.keycloak.authenticated === true;
  }

  /** Utilisé par le bouton utilisateur et le panneau latéral du gabarit. */
  user(): Observable<User> {
    return this.utilisateur$.asObservable();
  }

  change(): Observable<User> {
    return this.utilisateur$.asObservable();
  }

  logout(): Observable<boolean> {
    return from(this.keycloak.logout({ redirectUri: window.location.origin })).pipe(
      map(() => true),
    );
  }

  private lireJeton(): void {
    const jeton = this.keycloak.tokenParsed ?? {};
    const connus = Object.keys(LIBELLES_ROLES);
    // Les rôles peuvent être posés sur le realm ou sur le client, selon la
    // façon dont les comptes ont été créés dans Keycloak : on lit les deux.
    const roles: string[] = [
      ...(jeton.realm_access?.roles ?? []),
      ...(jeton.resource_access?.[config.keycloak.clientId]?.roles ?? []),
    ];
    this._roles.set(roles.map((r) => r.toUpperCase()).filter((r): r is Role => connus.includes(r)));
    this._nomUtilisateur.set(jeton['preferred_username'] ?? '');
    this._nomComplet.set(jeton['name'] ?? jeton['preferred_username'] ?? '');
    this.utilisateur$.next({
      id: jeton['sub'] ?? null,
      name: this._nomComplet(),
      email: jeton['email'] ?? '',
      avatar: 'images/avatar-default.jpg',
      roles: this.libellesRoles(),
    });
  }
}
