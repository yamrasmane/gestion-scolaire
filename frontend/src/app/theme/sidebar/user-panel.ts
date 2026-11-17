import { Component, ViewEncapsulation, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';

import { AuthService } from '@core/authentication';

@Component({
  selector: 'app-user-panel',
  template: `
    <div class="matero-user-panel">
      <img class="matero-user-panel-avatar" [src]="user()?.avatar" alt="" width="64" />
      <div class="matero-user-panel-info">
        <h4>{{ auth.nomComplet() }}</h4>
        <h5>{{ auth.libellesRoles().join(', ') || 'Aucun rôle' }}</h5>
      </div>
    </div>
  `,
  styleUrl: './user-panel.scss',
  encapsulation: ViewEncapsulation.None,
})
export class UserPanel {
  protected readonly auth = inject(AuthService);
  user = toSignal(this.auth.user());
}
