import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';

import { AnneeContexte } from '@core/annee-contexte';
import { AnneeScolaire, ApiService, messageErreur } from '@core/api.service';
import { AuthService } from '@core/authentication';
import { DIRECTION } from '@core/roles';
import { PageHeader } from '@shared/components/page-header/page-header';
import { BoutonFormulaire } from '@shared/composants/bouton-formulaire';
import { enIso } from '@shared/composants/dates';

@Component({
  selector: 'app-annees-scolaires',
  templateUrl: './annees-scolaires.html',
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatDatepickerModule,
    PageHeader,
    BoutonFormulaire,
  ],
})
export class AnneesScolaires {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly contexte = inject(AnneeContexte);

  protected readonly annees = signal<AnneeScolaire[]>([]);
  protected readonly chargement = signal(true);
  protected readonly enCours = signal(false);
  protected readonly erreur = signal('');
  protected readonly peutModifier = this.auth.aUnRole(...DIRECTION);

  protected readonly colonnes = this.peutModifier
    ? ['libelle', 'dateDebut', 'dateFin', 'composition', 'etat', 'actions']
    : ['libelle', 'dateDebut', 'dateFin', 'composition', 'etat'];

  protected readonly formulaire = this.fb.nonNullable.group({
    libelle: ['', [Validators.required, Validators.pattern(/^\d{4}-\d{4}$/)]],
    // Le calendrier Material travaille avec des objets Date ; la conversion
    // vers « 2026-10-01 » se fait à l'envoi.
    dateDebut: [null as Date | null, Validators.required],
    dateFin: [null as Date | null, Validators.required],
    avecCompositionDefaut: [true],
    poidsCompositionDefaut: [2, [Validators.required, Validators.min(0.01)]],
    bloquerBulletinImpaye: [false],
  });

  constructor() {
    this.charger();
  }

  protected creer(fenetre?: BoutonFormulaire): void {
    if (this.formulaire.invalid) {
      this.formulaire.markAllAsTouched();
      return;
    }
    const saisie = this.formulaire.getRawValue();
    this.executer(
      this.api.creerAnneeScolaire({
        ...saisie,
        dateDebut: enIso(saisie.dateDebut) ?? '',
        dateFin: enIso(saisie.dateFin) ?? '',
      }),
      () => {
        // La fenêtre ne se referme qu'une fois l'année créée.
        this.formulaire.reset({
          avecCompositionDefaut: true,
          poidsCompositionDefaut: 2,
          bloquerBulletinImpaye: false,
        });
        fenetre?.fermer();
      },
    );
  }

  protected activer(annee: AnneeScolaire): void {
    this.executer(this.api.activerAnneeScolaire(annee.id));
  }

  private executer(
    action: ReturnType<ApiService['activerAnneeScolaire']>,
    apres?: () => void,
  ): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: () => {
        apres?.();
        this.enCours.set(false);
        this.charger();
        this.contexte.charger();
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
