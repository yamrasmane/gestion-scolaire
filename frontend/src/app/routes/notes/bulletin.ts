import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ApiService, Bulletin as BulletinVue, messageErreur } from '@core/api.service';
import { BoutonDocument } from '@shared/composants/bouton-document';

/**
 * Bulletin trimestriel (SF-17, SF-18). L'aperçu à l'écran sert à vérifier les
 * notes ; le document remis à la famille est édité par le serveur à partir du
 * modèle Jasper « bulletin », au format choisi.
 */
@Component({
  selector: 'app-bulletin',
  templateUrl: './bulletin.html',
  styleUrl: './bulletin.scss',
  imports: [MatButtonModule, MatIconModule, BoutonDocument],
})
export class Bulletin {
  private readonly api = inject(ApiService);

  readonly inscriptionId = input.required<string>();
  readonly trimestreId = input.required<string>();

  protected readonly bulletin = signal<BulletinVue | null>(null);
  protected readonly erreur = signal('');

  constructor() {
    queueMicrotask(() => {
      this.api.bulletin(this.inscriptionId(), this.trimestreId()).subscribe({
        next: (bulletin) => this.bulletin.set(bulletin),
        error: (e) => this.erreur.set(messageErreur(e)),
      });
    });
  }
}
