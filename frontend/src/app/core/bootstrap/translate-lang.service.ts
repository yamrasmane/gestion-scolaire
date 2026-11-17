import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { SettingsService } from './settings.service';

@Injectable({
  providedIn: 'root',
})
export class TranslateLangService {
  private readonly translate = inject(TranslateService);
  private readonly settings = inject(SettingsService);

  load() {
    return new Promise<void>(resolve => {
      const defaultLang = this.settings.getTranslateLang();

      this.translate.setFallbackLang(defaultLang);
      this.translate.use(defaultLang).subscribe({
        // Si le fichier de traduction manque, l'application démarre quand même
        // (libellés affichés sous forme de clés) au lieu de rester bloquée sur
        // l'écran de chargement.
        error: () => {
          console.error(`Fichier de traduction « ${defaultLang} » introuvable.`);
          resolve();
        },
        complete: () => resolve(),
      });
    });
  }
}
