import { Component } from '@angular/core';
import { ErrorCode } from '@shared/components/error-code/error-code';

@Component({
  selector: 'app-error-404',
  template: `
    <error-code
      code="404"
      title="Page introuvable"
      message="La page demandée n’existe pas ou a été déplacée."
    />
  `,
  imports: [ErrorCode],
})
export class Error404 {}
