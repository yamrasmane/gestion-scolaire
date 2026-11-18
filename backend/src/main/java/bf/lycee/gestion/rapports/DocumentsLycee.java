package bf.lycee.gestion.rapports;

import bf.lycee.gestion.service.BulletinService;
import bf.lycee.gestion.service.CaisseService;
import bf.lycee.gestion.web.FinancesDto;
import bf.lycee.gestion.web.NotesDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Documents du lycée : reçus, bulletins, états des impayés, journal de caisse.
 *
 * <p>Chaque document se résume à deux choses : des <strong>paramètres</strong>
 * (l'entête, qui ne se répète pas) et des <strong>lignes</strong> (le tableau,
 * qui se répète). Les lignes sont des couples nom/valeur déjà mis en forme :
 * ce que voit {@link ServiceRapports} est exactement ce que voit le
 * maquettiste dans Jaspersoft Studio.
 */
@Service
public class DocumentsLycee {

    private final ServiceRapports rapports;
    private final CaisseService caisse;
    private final BulletinService bulletins;

    public DocumentsLycee(ServiceRapports rapports, CaisseService caisse, BulletinService bulletins) {
        this.rapports = rapports;
        this.caisse = caisse;
        this.bulletins = bulletins;
    }

    /** Un document prêt à produire : son modèle, ses paramètres et ses lignes. */
    public record Composition(String rapport, Map<String, Object> parametres,
                              List<Map<String, String>> lignes, String nomFichier) {
    }

    // ---------- Reçu de règlement (SF-30) ----------

    public Composition recu(UUID paiementId, boolean duplicata) {
        FinancesDto.Recu recu = caisse.recu(paiementId, duplicata);
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("NUMERO_RECU", recu.numeroRecu());
        parametres.put("DUPLICATA", recu.duplicata());
        parametres.put("DATE_PAIEMENT", date(recu.datePaiement()));
        parametres.put("ELEVE", recu.nom() + ' ' + recu.prenom());
        parametres.put("MATRICULE", recu.matricule());
        parametres.put("CLASSE", recu.classeNom());
        parametres.put("MONTANT", montant(recu.montant()));
        parametres.put("MONTANT_LETTRES", recu.montantEnLettres());
        parametres.put("MODE", libelleMode(recu.mode().name()));
        parametres.put("REFERENCE", texte(recu.reference()));
        parametres.put("PAYEUR", texte(recu.payeur()));
        parametres.put("CAISSIER", texte(recu.caissier()));
        parametres.put("STATUT", recu.statut().name());
        parametres.put("RESTE", montant(recu.resteAPayer()));

        List<Map<String, String>> lignes = new ArrayList<>();
        for (FinancesDto.ImputationVue imputation : recu.imputations()) {
            lignes.add(ligne("typeFrais", imputation.typeFraisLibelle(),
                    "tranche", texte(imputation.trancheLibelle()),
                    "montant", montant(imputation.montant())));
        }
        return new Composition("recu", parametres, lignes, "recu-" + recu.numeroRecu());
    }

    // ---------- Bulletin de notes (SF-17) ----------

    public Composition bulletin(UUID inscriptionId, UUID trimestreId) {
        NotesDto.BulletinVue bulletin = bulletins.bulletin(inscriptionId, trimestreId);
        NotesDto.StatistiquesClasse stats = bulletin.statistiques();
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("ELEVE", bulletin.nom() + ' ' + bulletin.prenom());
        parametres.put("MATRICULE", bulletin.matricule());
        parametres.put("CLASSE", bulletin.classeNom());
        parametres.put("ANNEE_SCOLAIRE", bulletin.anneeScolaire());
        parametres.put("TRIMESTRE", "Trimestre " + bulletin.trimestreNumero());
        parametres.put("REDOUBLANT", bulletin.redoublant() ? " (redoublant)" : "");
        parametres.put("MOYENNE_GENERALE", nombre(bulletin.moyenneGenerale()));
        parametres.put("TOTAL_POINTS", nombre(bulletin.totalPoints()));
        parametres.put("TOTAL_COEFFICIENTS", nombre(bulletin.totalCoefficients()));
        parametres.put("RANG", rang(bulletin.rang(), bulletin.exAequo()));
        parametres.put("MENTION", texte(bulletin.mention()));
        parametres.put("APPRECIATION", texte(bulletin.appreciationConseil()));
        parametres.put("EFFECTIF", stats == null ? "—" : String.valueOf(stats.effectif()));
        parametres.put("MOYENNE_CLASSE", stats == null ? "—" : nombre(stats.moyenneClasse()));
        parametres.put("PLUS_FORTE_MOYENNE", stats == null ? "—" : nombre(stats.plusForteMoyenne()));
        parametres.put("PLUS_FAIBLE_MOYENNE", stats == null ? "—" : nombre(stats.plusFaibleMoyenne()));
        parametres.put("OBSERVATIONS", String.join(" · ", bulletin.observations()));

        List<Map<String, String>> lignes = new ArrayList<>();
        for (NotesDto.LigneBulletin l : bulletin.lignes()) {
            Map<String, String> ligne = new LinkedHashMap<>();
            ligne.put("matiereCode", l.matiereCode());
            ligne.put("matiereLibelle", l.matiereLibelle());
            ligne.put("enseignant", texte(l.enseignant()));
            ligne.put("moyenneDevoirs", nombre(l.moyenneDevoirs()));
            ligne.put("composition", nombre(l.composition()));
            ligne.put("moyenne", l.nonClasse() ? "n.c." : nombre(l.moyenne()));
            ligne.put("coefficient", nombre(l.coefficient()));
            ligne.put("moyenneCoefficientee", nombre(l.moyenneCoefficientee()));
            ligne.put("rang", rang(l.rang(), l.exAequo()));
            ligne.put("appreciation", texte(l.appreciation()));
            lignes.add(ligne);
        }
        return new Composition("bulletin", parametres, lignes,
                "bulletin-" + bulletin.matricule() + "-T" + bulletin.trimestreNumero());
    }

    // ---------- État des impayés (SF-31) ----------

    public Composition impayes(UUID anneeId, UUID classeId, LocalDate limite, String recherche) {
        // Un état s'imprime en entier : on demande donc toutes les lignes.
        List<FinancesDto.ImpayeVue> impayes = caisse
                .impayes(anneeId, classeId, limite, recherche, Pageable.unpaged()).contenu();
        BigDecimal total = impayes.stream().map(FinancesDto.ImpayeVue::reste)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("ANNEE_SCOLAIRE", "");
        parametres.put("CLASSE", impayes.isEmpty() || classeId == null
                ? "toutes les classes" : impayes.get(0).classeNom());
        parametres.put("DATE_LIMITE", date(limite == null ? LocalDate.now() : limite));
        parametres.put("TOTAL_RESTE", montant(total));
        parametres.put("NOMBRE_LIGNES", String.valueOf(impayes.size()));

        List<Map<String, String>> lignes = new ArrayList<>();
        for (FinancesDto.ImpayeVue i : impayes) {
            Map<String, String> ligne = new LinkedHashMap<>();
            ligne.put("matricule", i.matricule());
            ligne.put("nom", i.nom());
            ligne.put("prenom", i.prenom());
            ligne.put("classeNom", i.classeNom());
            ligne.put("typeFraisLibelle", i.typeFraisLibelle());
            ligne.put("trancheLibelle", texte(i.trancheLibelle()));
            ligne.put("dateEcheance", date(i.dateEcheance()));
            ligne.put("montantDu", montant(i.montantDu()));
            ligne.put("montantPaye", montant(i.montantPaye()));
            ligne.put("reste", montant(i.reste()));
            ligne.put("statut", i.statut() == null ? "" : i.statut().name());
            lignes.add(ligne);
        }
        return new Composition("impayes", parametres, lignes, "impayes");
    }

    // ---------- Journal de caisse (SF-32) ----------

    public Composition journal(LocalDate journee) {
        LocalDate jour = journee == null ? LocalDate.now() : journee;
        FinancesDto.JournalCaisse journal = caisse.journal(jour, Pageable.unpaged());

        StringBuilder parMode = new StringBuilder();
        for (FinancesDto.TotalParMode t : journal.totauxParMode()) {
            if (!parMode.isEmpty()) {
                parMode.append("  ·  ");
            }
            parMode.append(libelleMode(t.mode().name())).append(" : ").append(montant(t.total()));
        }

        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("JOURNEE", date(jour));
        parametres.put("TOTAL", montant(journal.total()));
        parametres.put("TOTAUX_PAR_MODE", parMode.toString());

        List<Map<String, String>> lignes = new ArrayList<>();
        for (FinancesDto.PaiementVue p : journal.paiements().contenu()) {
            Map<String, String> ligne = new LinkedHashMap<>();
            ligne.put("numeroRecu", p.numeroRecu());
            ligne.put("datePaiement", date(p.datePaiement()));
            ligne.put("montant", montant(p.montant()));
            ligne.put("mode", libelleMode(p.mode().name()));
            ligne.put("reference", texte(p.reference()));
            ligne.put("payeur", texte(p.payeur()));
            ligne.put("caissier", texte(p.caissier()));
            ligne.put("statut", p.statut().name().equals("VALIDE") ? "Valide" : "Annulé");
            lignes.add(ligne);
        }
        return new Composition("journal-caisse", parametres, lignes, "journal-" + jour);
    }

    // ---------- Production ----------

    public ServiceRapports.Document produire(Composition composition, FormatRapport format) {
        ServiceRapports.Document document = rapports.produire(composition.rapport(),
                composition.lignes(), composition.parametres(), format);
        return new ServiceRapports.Document(document.contenu(),
                composition.nomFichier() + '.' + format.extension(), format);
    }

    /** Modèle de données JSON du document, pour le brancher dans Jaspersoft Studio. */
    public String modele(Composition composition) {
        return rapports.modeleJson(composition.lignes());
    }

    // ---------- Mise en forme ----------

    private static Map<String, String> ligne(String... clesEtValeurs) {
        Map<String, String> ligne = new LinkedHashMap<>();
        for (int i = 0; i < clesEtValeurs.length; i += 2) {
            ligne.put(clesEtValeurs[i], clesEtValeurs[i + 1]);
        }
        return ligne;
    }

    /** Montant en francs CFA : séparateur de milliers, sans décimale. */
    private static String montant(BigDecimal valeur) {
        if (valeur == null) {
            return "";
        }
        return String.format(Locale.FRANCE, "%,.0f", valeur).replace(' ', ' ');
    }

    /** Note ou coefficient : deux décimales au plus, virgule décimale. */
    private static String nombre(BigDecimal valeur) {
        if (valeur == null) {
            return "—";
        }
        return valeur.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String date(LocalDate date) {
        return date == null ? "" : String.format("%02d/%02d/%d", date.getDayOfMonth(),
                date.getMonthValue(), date.getYear());
    }

    private static String rang(Integer rang, boolean exAequo) {
        if (rang == null) {
            return "—";
        }
        return rang + (exAequo ? " ex æquo" : "");
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur;
    }

    private static String libelleMode(String mode) {
        return switch (mode) {
            case "ESPECES" -> "Espèces";
            case "MOBILE_MONEY" -> "Mobile Money";
            case "VIREMENT" -> "Virement";
            case "CHEQUE" -> "Chèque";
            default -> mode;
        };
    }
}
