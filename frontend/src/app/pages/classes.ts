import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { AnneeContexte } from '../core/annee-contexte';
import { ApiService, Classe, Cycle, Niveau, messageErreur } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { DIRECTION } from '../core/roles';

@Component({
  selector: 'app-classes',
  imports: [FormsModule],
  templateUrl: './classes.html',
})
export class Classes {
  private readonly api = inject(ApiService);
  protected readonly contexte = inject(AnneeContexte);
  protected readonly peutModifier = inject(AuthService).aUnRole(...DIRECTION);

  protected readonly classes = signal<Classe[]>([]);
  protected readonly niveaux = signal<Niveau[]>([]);
  protected readonly erreur = signal('');
  protected readonly enCours = signal(false);

  protected nouvelleClasse: { niveauId: number | null; nom: string; effectifMax: number } = {
    niveauId: null,
    nom: '',
    effectifMax: 60,
  };
  protected nouveauNiveau: { code: string; libelle: string; cycle: Cycle; ordre: number } = {
    code: '',
    libelle: '',
    cycle: 'PREMIER',
    ordre: 0,
  };

  constructor() {
    this.chargerNiveaux();
    effect(() => {
      const anneeId = this.contexte.anneeId();
      if (anneeId) {
        this.chargerClasses(anneeId);
      } else {
        this.classes.set([]);
      }
    });
  }

  protected libelleCycle(cycle: Cycle): string {
    return cycle === 'PREMIER' ? 'Premier cycle' : 'Second cycle';
  }

  protected creerClasse(): void {
    const anneeId = this.contexte.anneeId();
    const { niveauId, nom, effectifMax } = this.nouvelleClasse;
    if (!anneeId || !niveauId || !nom.trim()) {
      return;
    }
    this.executer(this.api.creerClasse(anneeId, { niveauId, nom, effectifMax }), () => {
      this.nouvelleClasse = { ...this.nouvelleClasse, nom: '' };
      this.chargerClasses(anneeId);
    });
  }

  protected creerNiveau(): void {
    if (!this.nouveauNiveau.code.trim() || !this.nouveauNiveau.libelle.trim()) {
      return;
    }
    this.executer(this.api.creerNiveau(this.nouveauNiveau), () => {
      this.nouveauNiveau = { code: '', libelle: '', cycle: 'PREMIER', ordre: 0 };
      this.chargerNiveaux();
    });
  }

  private executer(action: Observable<unknown>, apres: () => void): void {
    this.enCours.set(true);
    this.erreur.set('');
    action.subscribe({
      next: () => {
        this.enCours.set(false);
        apres();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  private chargerClasses(anneeId: number): void {
    this.api.classes(anneeId).subscribe({
      next: (classes) => this.classes.set(classes),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private chargerNiveaux(): void {
    this.api.niveaux().subscribe({
      next: (niveaux) => this.niveaux.set(niveaux),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }
}
