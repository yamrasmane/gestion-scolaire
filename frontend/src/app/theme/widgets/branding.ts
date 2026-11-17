import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-branding',
  template: `
    <a class="branding" routerLink="/accueil">
      <mat-icon class="branding-logo">school</mat-icon>
      @if (showName()) {
        <span class="branding-name">Gestion scolaire</span>
      }
    </a>
  `,
  styles: `
    .branding {
      display: flex;
      align-items: center;
      margin: 0 0.5rem;
      text-decoration: none;
      white-space: nowrap;
      color: inherit;
      border-radius: 50rem;
    }

    .branding-logo {
      width: 2rem;
      height: 2rem;
      font-size: 2rem;
    }

    .branding-name {
      margin: 0 0.5rem;
      font-size: 1rem;
      font-weight: 500;
    }
  `,
  imports: [RouterLink, MatIconModule],
})
export class Branding {
  readonly showName = input(true);
}
