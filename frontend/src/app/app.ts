import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AnneeContexte } from './core/annee-contexte';
import { AuthService } from './core/auth.service';
import { DIRECTION } from './core/roles';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly contexte = inject(AnneeContexte);
  protected readonly direction = this.auth.aUnRole(...DIRECTION);

  constructor() {
    this.contexte.charger();
  }

  protected choisirAnnee(evenement: Event): void {
    this.contexte.choisir(Number((evenement.target as HTMLSelectElement).value));
  }
}
