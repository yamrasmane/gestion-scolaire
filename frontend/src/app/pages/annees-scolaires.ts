import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AnneeScolaire, ApiService, messageErreur } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DIRECTION } from '../core/roles';

@Component({
  selector: 'app-annees-scolaires',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './annees-scolaires.html',
  styleUrl: './annees-scolaires.scss',
})
export class AnneesScolaires {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  protected readonly annees = signal<AnneeScolaire[]>([]);
  protected readonly chargement = signal(true);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');
  protected readonly peutModifier = this.auth.aUnRole(...DIRECTION);

  protected readonly formulaire = this.fb.nonNullable.group({
    libelle: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{4}$/)]],
    dateDebut: ['', Validators.required],
    dateFin: ['', Validators.required],
    avecCompositionDefaut: [true],
    poidsCompositionDefaut: [2, [Validators.required, Validators.min(0.01)]],
    bloquerBulletinImpaye: [false],
  });

  constructor() {
    this.charger();
  }

  protected creer(): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    this.executer(this.api.creerAnneeScolaire(this.formulaire.getRawValue()), () =>
      this.formulaire.reset(),
    );
  }

  protected activer(annee: AnneeScolaire): void {
    this.executer(this.api.activerAnneeScolaire(annee.id));
  }

  private executer(action: ReturnType<ApiService['activerAnneeScolaire']>, apres?: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: () => {
        apres?.();
        this.enCours.set(false);
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private charger(): void {
    this.api.anneesScolaires().subscribe({
      next: (annees) => {
        this.annees.set(annees);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }
}
