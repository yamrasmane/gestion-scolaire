import { Component } from '@angular/core';
import { ErrorCode } from '@shared/components/error-code/error-code';

@Component({
  selector: 'app-error-500',
  template: `
    <error-code
      code="500"
      title="Erreur du serveur"
      message="Une erreur inattendue est survenue. Réessayez dans un instant."
    />
  `,
  imports: [ErrorCode],
})
export class Error500 {}
