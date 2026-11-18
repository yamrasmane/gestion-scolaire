import { format, isValid, parseISO } from 'date-fns';

/**
 * Conversions entre les dates du calendrier Material (objets `Date`) et le
 * format attendu par l'API (`yyyy-MM-dd`, sans heure ni fuseau).
 */

/** Date vers « 2026-03-15 », ou `null` si rien n'est saisi. */
export function enIso(valeur: Date | string | null | undefined): string | null {
  if (!valeur) {
    return null;
  }
  if (typeof valeur === 'string') {
    return valeur;
  }
  return isValid(valeur) ? format(valeur, 'yyyy-MM-dd') : null;
}

/** « 2026-03-15 » vers une Date, pour pré-remplir un calendrier. */
export function depuisIso(valeur: string | null | undefined): Date | null {
  if (!valeur) {
    return null;
  }
  const date = parseISO(valeur);
  return isValid(date) ? date : null;
}

/** Aujourd'hui, au format de l'API. */
export function aujourdhui(): string {
  return format(new Date(), 'yyyy-MM-dd');
}
