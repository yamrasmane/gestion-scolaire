/**
 * Paramètres de l'application. Les valeurs par défaut correspondent à
 * l'environnement de développement (docker-compose). En production, on peut
 * les surcharger sans recompiler en définissant `window.__LYCEE_CONFIG__`
 * dans index.html (ou un script servi par le serveur web).
 */
export interface ConfigApplication {
  apiUrl: string;
  keycloak: { url: string; realm: string; clientId: string };
}

const parDefaut: ConfigApplication = {
  apiUrl: 'http://localhost:8080/api',
  keycloak: { url: 'http://localhost:8081', realm: 'lycee', clientId: 'lycee-frontend' },
};

const surcharge =
  (globalThis as { __LYCEE_CONFIG__?: Partial<ConfigApplication> }).__LYCEE_CONFIG__ ?? {};

export const config: ConfigApplication = {
  ...parDefaut,
  ...surcharge,
  keycloak: { ...parDefaut.keycloak, ...surcharge.keycloak },
};
