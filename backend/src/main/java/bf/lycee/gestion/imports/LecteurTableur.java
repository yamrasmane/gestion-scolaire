package bf.lycee.gestion.imports;

import bf.lycee.gestion.service.SaisieInvalideException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lit un fichier Excel (.xlsx, .xls) ou CSV dont la première ligne contient
 * les en-têtes. Les en-têtes sont comparés sans tenir compte de la casse,
 * des accents, des espaces ni de la ponctuation ; des synonymes sont acceptés.
 */
@Component
public class LecteurTableur {

    /** Garde-fou contre les fichiers démesurés. */
    public static final int MAX_LIGNES = 2000;

    /** Une ligne de données, repérée par son numéro dans le fichier (1 = ligne d'en-têtes). */
    public record Ligne(int numero, Map<String, String> valeurs) {

        /** Valeur de la colonne, jamais nulle (chaîne vide si absente). */
        public String get(String colonne) {
            return valeurs.getOrDefault(colonne, "");
        }
    }

    /**
     * @param colonnes colonnes attendues : nom canonique → synonymes acceptés (déjà normalisés ou non)
     * @param obligatoires noms canoniques des colonnes qui doivent figurer dans le fichier
     */
    public List<Ligne> lire(MultipartFile fichier, Map<String, List<String>> colonnes, List<String> obligatoires) {
        if (fichier == null || fichier.isEmpty()) {
            throw new SaisieInvalideException("Le fichier est vide");
        }
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename().toLowerCase(Locale.ROOT);
        List<List<String>> brut;
        try {
            byte[] contenu = fichier.getBytes();
            if (nom.endsWith(".csv") || nom.endsWith(".txt")) {
                brut = lireCsv(contenu);
            } else if (nom.endsWith(".xlsx") || nom.endsWith(".xls")) {
                brut = lireExcel(contenu);
            } else {
                throw new SaisieInvalideException("Format non pris en charge : utilisez un fichier .xlsx, .xls ou .csv");
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof SaisieInvalideException saisieInvalide) {
                throw saisieInvalide;
            }
            throw new SaisieInvalideException("Le fichier est illisible ou endommagé");
        }
        if (brut.isEmpty()) {
            throw new SaisieInvalideException("Le fichier ne contient aucune ligne");
        }

        // Correspondance en-tête normalisé → nom canonique
        Map<String, String> synonymes = new LinkedHashMap<>();
        colonnes.forEach((canonique, alias) -> {
            synonymes.put(normaliser(canonique), canonique);
            alias.forEach(a -> synonymes.put(normaliser(a), canonique));
        });
        List<String> entetes = brut.get(0).stream().map(e -> synonymes.get(normaliser(e))).toList();
        List<String> manquantes = obligatoires.stream().filter(o -> !entetes.contains(o)).toList();
        if (!manquantes.isEmpty()) {
            throw new SaisieInvalideException("Colonnes manquantes dans le fichier : " + String.join(", ", manquantes));
        }

        List<Ligne> lignes = new ArrayList<>();
        for (int i = 1; i < brut.size(); i++) {
            List<String> cellules = brut.get(i);
            Map<String, String> valeurs = new LinkedHashMap<>();
            boolean vide = true;
            for (int c = 0; c < entetes.size(); c++) {
                String valeur = c < cellules.size() ? cellules.get(c).trim() : "";
                if (!valeur.isEmpty()) {
                    vide = false;
                }
                if (entetes.get(c) != null) {
                    valeurs.put(entetes.get(c), valeur);
                }
            }
            if (!vide) {
                lignes.add(new Ligne(i + 1, valeurs));
            }
        }
        if (lignes.isEmpty()) {
            throw new SaisieInvalideException("Le fichier ne contient aucune ligne de données");
        }
        if (lignes.size() > MAX_LIGNES) {
            throw new SaisieInvalideException("Le fichier dépasse la limite de " + MAX_LIGNES + " lignes");
        }
        return lignes;
    }

    /** Minuscules, sans accents, sans espaces ni ponctuation : « Date de naissance » → « datedenaissance ». */
    public static String normaliser(String texte) {
        if (texte == null) {
            return "";
        }
        return Normalizer.normalize(texte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private static List<List<String>> lireExcel(byte[] contenu) throws IOException {
        List<List<String>> lignes = new ArrayList<>();
        DataFormatter formateur = new DataFormatter(Locale.FRANCE);
        try (Workbook classeur = WorkbookFactory.create(new ByteArrayInputStream(contenu))) {
            Sheet feuille = classeur.getSheetAt(0);
            int derniere = feuille.getLastRowNum();
            for (int r = 0; r <= derniere; r++) {
                Row rangee = feuille.getRow(r);
                List<String> cellules = new ArrayList<>();
                if (rangee != null) {
                    for (int c = 0; c < rangee.getLastCellNum(); c++) {
                        cellules.add(texte(rangee.getCell(c), formateur));
                    }
                }
                // Les lignes vides sont conservées pour garder les bons numéros de ligne
                lignes.add(cellules);
            }
        }
        return lignes;
    }

    private static String texte(Cell cellule, DataFormatter formateur) {
        if (cellule == null) {
            return "";
        }
        if (cellule.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellule)) {
            // Les dates Excel sont renvoyées au format ISO (2012-03-25)
            return cellule.getLocalDateTimeCellValue().toLocalDate().toString();
        }
        return formateur.formatCellValue(cellule).trim();
    }

    private static List<List<String>> lireCsv(byte[] contenu) throws IOException {
        String texte = new String(contenu, StandardCharsets.UTF_8);
        if (texte.startsWith("﻿")) {
            texte = texte.substring(1);
        }
        String premiereLigne = texte.lines().findFirst().orElse("");
        char separateur = compter(premiereLigne, ';') >= compter(premiereLigne, ',') ? ';' : ',';
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(separateur)
                .setIgnoreEmptyLines(false)
                .setTrim(true)
                .build();
        List<List<String>> lignes = new ArrayList<>();
        try (CSVParser analyseur = format.parse(new StringReader(texte))) {
            for (CSVRecord enregistrement : analyseur) {
                List<String> cellules = new ArrayList<>();
                enregistrement.forEach(cellules::add);
                lignes.add(cellules);
            }
        }
        return lignes;
    }

    private static long compter(String texte, char caractere) {
        return texte.chars().filter(c -> c == caractere).count();
    }
}
