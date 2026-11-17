import { Component } from '@angular/core';
import { ErrorCode } from '@shared/components/error-code/error-code';

@Component({
  selector: 'app-error-403',
  template: `
    <error-code
      code="403"
      title="Accès refusé"
      message="Votre rôle ne vous permet pas d’ouvrir cette page."
    />
  `,
  imports: [ErrorCode],
})
export class Error403 {}
