import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  LOCALE_ID,
  importProvidersFrom,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';

import { provideDateFnsAdapter } from '@angular/material-date-fns-adapter';
import { MAT_CARD_CONFIG } from '@angular/material/card';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import { MAT_DATE_LOCALE } from '@angular/material/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideDateFnsDatetimeAdapter } from '@ng-matero/extensions-date-fns-adapter';
import { FORMLY_CONFIG, provideFormlyCore } from '@ngx-formly/core';
import { withFormlyMaterial } from '@ngx-formly/material';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { provideHotToastConfig } from '@ngxpert/hot-toast';
import { NgxPermissionsModule } from 'ngx-permissions';

import {
  AuthService,
  BASE_URL,
  interceptors,
  SettingsService,
  StartupService,
  TranslateLangService,
} from '@core';
import { environment } from '@env/environment';
import { formlyConfigFactory, PaginatorI18nService } from '@shared';
import { routes } from './app.routes';

// Montants et dates à la française : 125 000, 15/03/2026.
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: BASE_URL, useValue: environment.baseUrl },
    // Un seul initialisateur, car Angular lance tous les initialisateurs en
    // parallèle : séparés, le menu se construisait avant l'arrivée du jeton
    // Keycloak et les entrées réservées à un rôle restaient cachées.
    // Les injections se font avant le premier `await` : le contexte
    // d'injection n'existe plus après.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      const langue = inject(TranslateLangService);
      const demarrage = inject(StartupService);
      return (async () => {
        await auth.initialiser();
        await langue.load();
        await demarrage.load();
      })();
    }),
    provideHttpClient(withInterceptors(interceptors)),
    provideRouter(
      routes,
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
      withComponentInputBinding(),
    ),
    provideHotToastConfig(),
    provideTranslateService({
      loader: provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json' }),
    }),
    importProvidersFrom(NgxPermissionsModule.forRoot()),
    provideFormlyCore([...withFormlyMaterial()]),
    {
      provide: FORMLY_CONFIG,
      useFactory: formlyConfigFactory,
      deps: [TranslateService],
      multi: true,
    },
    {
      provide: MatPaginatorIntl,
      deps: [PaginatorI18nService],
      useFactory: (paginatorI18nSrv: PaginatorI18nService) => paginatorI18nSrv.getPaginatorIntl(),
    },
    {
      provide: MAT_DATE_LOCALE,
      useFactory: () => inject(SettingsService).getLocale(),
    },
    {
      // Champs cernés d'un filet et fond blanc, comme sur les maquettes ; le
      // message d'erreur ne réserve plus sa place sous chaque champ.
      provide: MAT_FORM_FIELD_DEFAULT_OPTIONS,
      useValue: { appearance: 'outline', subscriptSizing: 'dynamic' },
    },
    {
      provide: MAT_CARD_CONFIG,
      useValue: {
        appearance: 'outlined',
      },
    },
    provideDateFnsAdapter({
      parse: {
        dateInput: 'dd/MM/yyyy',
      },
      display: {
        dateInput: 'dd/MM/yyyy',
        monthYearLabel: 'MMM yyyy',
        dateA11yLabel: 'dd MMMM yyyy',
        monthYearA11yLabel: 'MMMM yyyy',
      },
    }),
    provideDateFnsDatetimeAdapter({
      parse: {
        dateInput: 'dd/MM/yyyy',
        yearInput: 'yyyy',
        monthInput: 'MMMM',
        datetimeInput: 'dd/MM/yyyy HH:mm',
        timeInput: 'HH:mm',
      },
      display: {
        dateInput: 'dd/MM/yyyy',
        yearInput: 'yyyy',
        monthInput: 'MMMM',
        datetimeInput: 'dd/MM/yyyy HH:mm',
        timeInput: 'HH:mm',
        monthYearLabel: 'MMMM yyyy',
        dateA11yLabel: 'dd MMMM yyyy',
        monthYearA11yLabel: 'MMMM yyyy',
        popupHeaderDateLabel: 'E dd MMM',
      },
    }),
  ],
};
