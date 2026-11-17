import { Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { ApiService, Bulletin as BulletinVue, messageErreur } from '@core/api.service';

/**
 * Bulletin trimestriel imprimable (SF-17, SF-18). Le navigateur se charge de
 * l'impression ou de l'enregistrement en PDF.
 */
@Component({
  selector: 'app-bulletin',
  templateUrl: './bulletin.html',
  styleUrl: './bulletin.scss',
  imports: [MatButtonModule, MatIconModule],
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

  protected imprimer(): void {
    window.print();
  }
}
