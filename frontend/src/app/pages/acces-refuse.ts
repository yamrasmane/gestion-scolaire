import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-acces-refuse',
  imports: [RouterLink],
  template: `
    <div class="carte">
      <h1>Accès refusé</h1>
      <p>Votre rôle ne vous permet pas d'ouvrir cette page.</p>
      <a routerLink="/">Retour à l'accueil</a>
    </div>
  `,
})
export class AccesRefuse {}
