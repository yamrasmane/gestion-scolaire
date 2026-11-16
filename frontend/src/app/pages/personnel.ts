import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, Personnel as Membre, messageErreur } from '../core/api.service';
import { ImportFichier } from '../core/import-fichier';
import { LIBELLES_ROLES, Role } from '../core/roles';

@Component({
  selector: 'app-personnel',
  imports: [FormsModule, ImportFichier],
  templateUrl: './personnel.html',
})
export class Personnel {
  private readonly api = inject(ApiService);

  protected readonly roles = Object.entries(LIBELLES_ROLES) as [Role, string][];
  protected readonly membres = signal<Membre[]>([]);
  protected readonly erreur = signal('');
  protected readonly information = signal('');
  protected readonly enCours = signal(false);

  protected nouveau = this.vide();

  constructor() {
    this.charger();
  }

  protected libelleRole(role: string): string {
    return LIBELLES_ROLES[role as Role] ?? role;
  }

  protected creer(): void {
    this.enCours.set(true);
    this.erreur.set('');
    this.information.set('');
    this.api.creerPersonnel(this.nouveau).subscribe({
      next: (resultat) => {
        this.information.set(resultat.avertissement ?? '');
        this.nouveau = this.vide();
        this.enCours.set(false);
        this.charger();
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.enCours.set(false);
      },
    });
  }

  protected basculer(membre: Membre): void {
    this.erreur.set('');
    this.api.activerPersonnel(membre.id, !membre.actif).subscribe({
      next: () => this.charger(),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected charger(): void {
    this.api.personnel().subscribe({
      next: (membres) => this.membres.set(membres),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private vide() {
    return { matricule: '', nom: '', prenom: '', email: '', telephone: '', role: 'ENSEIGNANT' };
  }
}
