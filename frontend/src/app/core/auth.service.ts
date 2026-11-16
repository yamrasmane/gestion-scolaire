import { Injectable, computed, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { config } from './config';
import { LIBELLES_ROLES, Role } from './roles';

/** Connexion unique (SSO) via Keycloak : OpenID Connect, flux « code » avec PKCE. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly keycloak = new Keycloak(config.keycloak);

  private readonly _nomComplet = signal('');
  private readonly _nomUtilisateur = signal('');
  private readonly _roles = signal<Role[]>([]);

  readonly nomComplet = this._nomComplet.asReadonly();
  readonly nomUtilisateur = this._nomUtilisateur.asReadonly();
  readonly roles = this._roles.asReadonly();
  readonly libellesRoles = computed(() => this._roles().map((role) => LIBELLES_ROLES[role]));

  /** Appelé au démarrage : redirige vers la page de connexion Keycloak si nécessaire. */
  async initialiser(): Promise<void> {
    await this.keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
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

  deconnecter(): Promise<void> {
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }

  private lireJeton(): void {
    const jeton = this.keycloak.tokenParsed ?? {};
    const connus = Object.keys(LIBELLES_ROLES);
    const roles: string[] = jeton.realm_access?.roles ?? [];
    this._roles.set(roles.map((r) => r.toUpperCase()).filter((r): r is Role => connus.includes(r)));
    this._nomUtilisateur.set(jeton['preferred_username'] ?? '');
    this._nomComplet.set(jeton['name'] ?? jeton['preferred_username'] ?? '');
  }
}
