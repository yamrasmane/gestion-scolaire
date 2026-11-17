export * from './base-url-interceptor';
export * from './settings-interceptor';
export * from './token-interceptor';
export * from './error-interceptor';

import { baseUrlInterceptor } from './base-url-interceptor';
import { errorInterceptor } from './error-interceptor';
import { settingsInterceptor } from './settings-interceptor';
import { tokenInterceptor } from './token-interceptor';

// Intercepteurs HTTP, de l'extérieur vers l'intérieur.
export const interceptors = [
  baseUrlInterceptor,
  settingsInterceptor,
  tokenInterceptor,
  errorInterceptor,
];
