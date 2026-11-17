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

  /** Appelé au démarrage : redirige vers la page de connexion Keycloak si nécessaire. */
  async initialiser(): Promise<void> {
    await this.keycloak.init({
      onLoad: 'login-required',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
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
      await this.keycloak.login();
    }
    return this.keycloak.token;
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
    const roles: string[] = jeton.realm_access?.roles ?? [];
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
