package bf.lycee.gestion.rapports;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JsonQLDataSource;
import net.sf.jasperreports.engine.export.HtmlExporter;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleHtmlExporterOutput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Moteur d'édition : un seul service pour tous les documents de l'application.
 *
 * <p>On lui donne le <strong>nom du fichier Jasper</strong>, les
 * <strong>données</strong> (n'importe quel objet : enregistrement, liste, carte)
 * et les <strong>paramètres</strong> d'entête, et il rend le document dans le
 * format demandé.
 *
 * <p>Les données sont transmises à Jasper <strong>en JSON</strong> : le modèle
 * de données du rapport est donc celui que l'API renvoie déjà, et le .jrxml
 * peut être rouvert dans Jaspersoft Studio avec un adaptateur « JSON file »
 * pointant sur ce même modèle (voir {@link #modeleJson}).
 *
 * <p>Les fichiers sont cherchés d'abord dans le dossier externe
 * {@code lycee.rapports.dossier}, puis dans le classpath ({@code rapports/}).
 * Un .jasper déposé dans le dossier externe est donc pris en compte sans
 * reconstruire l'application ; un .jrxml y est compilé à la volée. Le cache
 * suit la date du fichier : une modification est reprise sans redémarrage.
 */
@Service
public class ServiceRapports {

    private static final Logger log = LoggerFactory.getLogger(ServiceRapports.class);

    private final ObjectMapper json;
    private final EtablissementProperties etablissement;
    private final Path dossierExterne;
    private final Map<String, Compile> cache = new ConcurrentHashMap<>();

    /** Un rapport compilé et la date du fichier dont il provient. */
    private record Compile(JasperReport rapport, long horodatage) {
    }

    /** Document produit, prêt à être renvoyé ou enregistré. */
    public record Document(byte[] contenu, String nomFichier, FormatRapport format) {
    }

    public ServiceRapports(ObjectMapper json, EtablissementProperties etablissement,
                           @Value("${lycee.rapports.dossier:}") String dossier) {
        this.json = json;
        this.etablissement = etablissement;
        this.dossierExterne = dossier == null || dossier.isBlank() ? null : Path.of(dossier);
    }

    /**
     * Produit un document.
     *
     * @param nomRapport nom du fichier Jasper, sans extension (« recu », « bulletin »…)
     * @param donnees    données du document, sérialisées en JSON pour Jasper
     * @param parametres paramètres du rapport ; ceux de l'établissement sont ajoutés
     * @param format     format de sortie
     */
    public Document produire(String nomRapport, Object donnees, Map<String, Object> parametres,
                             FormatRapport format) {
        try {
            JasperReport rapport = charger(nomRapport);
            JasperPrint impression = remplir(rapport, parametres, donnees);
            byte[] contenu = exporter(impression, format);
            return new Document(contenu, nomRapport + '.' + format.extension(), format);
        } catch (JRException | IOException e) {
            throw new RapportException("Édition impossible du document « " + nomRapport + " » : "
                    + e.getMessage(), e);
        }
    }

    /**
     * Le modèle de données du rapport, en JSON indenté : c'est le fichier à
     * donner à Jaspersoft Studio comme adaptateur de données pour redessiner
     * le .jrxml sans rien deviner des champs disponibles.
     */
    public String modeleJson(Object donnees) {
        try {
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(racine(donnees));
        } catch (IOException e) {
            throw new RapportException("Modèle de données illisible : " + e.getMessage(), e);
        }
    }

    // ---------- Chargement des fichiers Jasper ----------

    private JasperReport charger(String nom) throws JRException, IOException {
        Path externeCompile = chemin(nom + ".jasper");
        if (externeCompile != null) {
            return depuisCache(nom, Files.getLastModifiedTime(externeCompile).toMillis(),
                    () -> (JasperReport) JRLoader.loadObject(externeCompile.toFile()));
        }
        Path externeSource = chemin(nom + ".jrxml");
        if (externeSource != null) {
            return depuisCache(nom, Files.getLastModifiedTime(externeSource).toMillis(),
                    () -> JasperCompileManager.compileReport(externeSource.toString()));
        }
        ClassPathResource compile = new ClassPathResource("rapports/" + nom + ".jasper");
        if (compile.exists()) {
            return depuisCache(nom, 0L, () -> {
                try (InputStream flux = compile.getInputStream()) {
                    return (JasperReport) JRLoader.loadObject(flux);
                }
            });
        }
        ClassPathResource source = new ClassPathResource("rapports/" + nom + ".jrxml");
        if (!source.exists()) {
            throw new RapportException("Aucun fichier Jasper nommé « " + nom
                    + " » (cherché dans " + (dossierExterne == null ? "le classpath" : dossierExterne)
                    + " puis dans rapports/ du classpath)");
        }
        return depuisCache(nom, 0L, () -> {
            try (InputStream flux = source.getInputStream()) {
                log.info("Compilation du rapport {} à la volée", nom);
                return JasperCompileManager.compileReport(flux);
            }
        });
    }

    /** Fichier du dossier externe s'il existe, sinon {@code null}. */
    private Path chemin(String fichier) {
        if (dossierExterne == null) {
            return null;
        }
        Path candidat = dossierExterne.resolve(fichier);
        return Files.isReadable(candidat) ? candidat : null;
    }

    private interface Compilation {
        JasperReport executer() throws JRException, IOException;
    }

    private JasperReport depuisCache(String nom, long horodatage, Compilation compilation)
            throws JRException, IOException {
        Compile connu = cache.get(nom);
        if (connu != null && connu.horodatage() == horodatage) {
            return connu.rapport();
        }
        JasperReport rapport = compilation.executer();
        cache.put(nom, new Compile(rapport, horodatage));
        return rapport;
    }

    // ---------- Données et paramètres ----------

    /**
     * Remplit le rapport avec les données.
     *
     * <p>Quand le .jrxml porte une requête {@code jsonql} — c'est le cas de
     * tous les modèles livrés —, le flux JSON est passé en paramètre et c'est
     * <strong>la requête du fichier Jasper</strong> qui choisit le nœud sur
     * lequel itérer : un maquettiste peut donc changer ce nœud dans Jaspersoft
     * Studio sans toucher au code. Sans requête, on se rabat sur les lignes.
     */
    private JasperPrint remplir(JasperReport rapport, Map<String, Object> parametres,
                                Object donnees) throws JRException, IOException {
        byte[] contenu = json.writeValueAsBytes(racine(donnees));
        Map<String, Object> tous = parametresComplets(parametres);
        boolean requeteJson = rapport.getQuery() != null
                && "jsonql".equalsIgnoreCase(rapport.getQuery().getLanguage());
        if (requeteJson) {
            // Noms des paramètres intégrés de l'exécuteur de requêtes JSON.
            tous.put("JSON_INPUT_STREAM", new ByteArrayInputStream(contenu));
            tous.put("JSON_DATE_PATTERN", "yyyy-MM-dd");
            tous.put("JSON_NUMBER_PATTERN", "#0.###");
            tous.put("JSON_LOCALE", java.util.Locale.FRANCE);
            return JasperFillManager.fillReport(rapport, tous);
        }
        return JasperFillManager.fillReport(rapport, tous, sourceJson(contenu));
    }

    /** Source de repli, positionnée d'office sur les lignes du document. */
    private JsonQLDataSource sourceJson(byte[] contenu) throws JRException {
        JsonQLDataSource source = new JsonQLDataSource(new ByteArrayInputStream(contenu), "lignes");
        source.setDatePattern("yyyy-MM-dd");
        source.setNumberPattern("#0.###");
        return source;
    }

    /**
     * Modèle uniforme : les lignes répétées d'un document vivent toujours sous
     * « lignes », l'entête se lit dans les paramètres.
     */
    private Object racine(Object donnees) {
        if (donnees instanceof java.util.Collection<?> liste) {
            return Map.of("lignes", liste);
        }
        return donnees == null ? Map.of("lignes", java.util.List.of()) : donnees;
    }

    /** Paramètres du rapport : ceux de l'appelant, plus l'entête de l'établissement. */
    private Map<String, Object> parametresComplets(Map<String, Object> parametres) {
        Map<String, Object> tous = new HashMap<>();
        tous.put("ETABLISSEMENT", etablissement.nom());
        tous.put("MINISTERE", etablissement.ministere());
        tous.put("DIRECTION", etablissement.direction());
        tous.put("ADRESSE", etablissement.adresse());
        tous.put("TELEPHONE", etablissement.telephone());
        tous.put("COURRIEL", etablissement.email());
        tous.put("DEVISE", etablissement.devise());
        tous.put("LOGO", etablissement.logo());
        tous.put("DATE_EDITION", LocalDate.now().toString());
        if (parametres != null) {
            tous.putAll(parametres);
        }
        tous.put(JRParameter.REPORT_LOCALE, java.util.Locale.FRANCE);
        return tous;
    }

    // ---------- Export ----------

    private byte[] exporter(JasperPrint impression, FormatRapport format) throws JRException {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        switch (format) {
            case PDF -> {
                JRPdfExporter exporteur = new JRPdfExporter();
                exporteur.setExporterInput(new SimpleExporterInput(impression));
                exporteur.setExporterOutput(new SimpleOutputStreamExporterOutput(sortie));
                exporteur.exportReport();
            }
            case XLSX -> {
                JRXlsxExporter exporteur = new JRXlsxExporter();
                exporteur.setExporterInput(new SimpleExporterInput(impression));
                exporteur.setExporterOutput(new SimpleOutputStreamExporterOutput(sortie));
                SimpleXlsxReportConfiguration reglages = new SimpleXlsxReportConfiguration();
                // Un classeur se lit en continu : ni saut de page ni cellules vides
                // de calage, et des nombres qui restent des nombres.
                reglages.setOnePagePerSheet(false);
                reglages.setRemoveEmptySpaceBetweenRows(true);
                reglages.setRemoveEmptySpaceBetweenColumns(true);
                reglages.setDetectCellType(true);
                reglages.setWhitePageBackground(false);
                reglages.setCollapseRowSpan(true);
                exporteur.setConfiguration(reglages);
                exporteur.exportReport();
            }
            case DOCX -> {
                JRDocxExporter exporteur = new JRDocxExporter();
                exporteur.setExporterInput(new SimpleExporterInput(impression));
                exporteur.setExporterOutput(new SimpleOutputStreamExporterOutput(sortie));
                exporteur.exportReport();
            }
            case CSV -> {
                JRCsvExporter exporteur = new JRCsvExporter();
                exporteur.setExporterInput(new SimpleExporterInput(impression));
                StringWriter texte = new StringWriter();
                exporteur.setExporterOutput(new SimpleWriterExporterOutput(texte));
                exporteur.exportReport();
                // Marque d'ordre des octets : Excel ouvre alors l'UTF-8 sans y toucher.
                return ('﻿' + texte.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            case HTML -> {
                HtmlExporter exporteur = new HtmlExporter();
                exporteur.setExporterInput(new SimpleExporterInput(impression));
                exporteur.setExporterOutput(new SimpleHtmlExporterOutput(sortie));
                exporteur.exportReport();
            }
            default -> throw new RapportException("Format non pris en charge : " + format);
        }
        return sortie.toByteArray();
    }
}
