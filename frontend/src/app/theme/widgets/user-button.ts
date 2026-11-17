import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe } from '@ngx-translate/core';

import { AuthService, SettingsService } from '@core';

@Component({
  selector: 'app-user',
  template: `
    <button matIconButton [matMenuTriggerFor]="menu" aria-label="Mon compte">
      <img class="avatar" [src]="user()?.avatar" width="24" alt="" />
    </button>

    <mat-menu #menu="matMenu">
      <div class="identite">
        <div class="nom">{{ auth.nomComplet() }}</div>
        <div class="roles">{{ auth.libellesRoles().join(', ') || 'Aucun rôle' }}</div>
      </div>
      <mat-divider />
      <button mat-menu-item (click)="restore()">
        <mat-icon>restore</mat-icon>
        <span>{{ 'restore_defaults' | translate }}</span>
      </button>
      <button mat-menu-item (click)="logout()">
        <mat-icon>exit_to_app</mat-icon>
        <span>{{ 'logout' | translate }}</span>
      </button>
    </mat-menu>
  `,
  styles: `
    .avatar {
      width: 1.5rem;
      height: 1.5rem;
      border-radius: 50rem;
    }

    .identite {
      padding: 0.5rem 1rem;
      line-height: 1.4;
    }

    .nom {
      font-weight: 500;
    }

    .roles {
      font-size: 0.75rem;
      opacity: 0.7;
    }
  `,
  imports: [MatButtonModule, MatDividerModule, MatIconModule, MatMenuModule, TranslatePipe],
})
export class UserButton {
  protected readonly auth = inject(AuthService);
  private readonly settings = inject(SettingsService);

  user = toSignal(this.auth.user());

  logout() {
    this.auth.logout().subscribe();
  }

  restore() {
    this.settings.reset();
    window.location.reload();
  }
}
