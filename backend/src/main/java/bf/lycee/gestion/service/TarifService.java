package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.Classe;
import bf.lycee.gestion.domain.GrilleTarifaire;
import bf.lycee.gestion.domain.Niveau;
import bf.lycee.gestion.domain.TypeFrais;
import bf.lycee.gestion.domain.enums.CategorieEleve;
import bf.lycee.gestion.domain.enums.CategorieTarif;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.repository.ClasseRepository;
import bf.lycee.gestion.repository.GrilleTarifaireRepository;
import bf.lycee.gestion.repository.NiveauRepository;
import bf.lycee.gestion.repository.TypeFraisRepository;
import bf.lycee.gestion.web.FinancesDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue des types de frais (SF-21) et grille tarifaire par niveau ou classe
 * et par catégorie d'élève (SF-22).
 */
@Service
@Transactional
public class TarifService {

    private final TypeFraisRepository typesFrais;
    private final GrilleTarifaireRepository grilles;
    private final AnneeScolaireRepository annees;
    private final NiveauRepository niveaux;
    private final ClasseRepository classes;

    public TarifService(TypeFraisRepository typesFrais, GrilleTarifaireRepository grilles,
                        AnneeScolaireRepository annees, NiveauRepository niveaux, ClasseRepository classes) {
        this.typesFrais = typesFrais;
        this.grilles = grilles;
        this.annees = annees;
        this.niveaux = niveaux;
        this.classes = classes;
    }

    // ---------- Types de frais ----------

    @Transactional(readOnly = true)
    public List<FinancesDto.TypeFraisVue> listerTypes(boolean actifsSeulement) {
        List<TypeFrais> liste = actifsSeulement
                ? typesFrais.findByActifTrueOrderByPrioriteImputationAscLibelleAsc()
                : typesFrais.findAllByOrderByPrioriteImputationAscLibelleAsc();
        return liste.stream().map(TarifService::versVue).toList();
    }

    public FinancesDto.TypeFraisVue creerType(FinancesDto.TypeFraisSaisie saisie) {
        String code = saisie.code().trim().toUpperCase();
        if (typesFrais.existsByCodeIgnoreCase(code)) {
            throw new RegleMetierException("Un type de frais porte déjà le code " + code);
        }
        TypeFrais type = new TypeFrais();
        type.setCode(code);
        appliquer(type, saisie);
        type.setActif(true);
        return versVue(typesFrais.save(type));
    }

    public FinancesDto.TypeFraisVue modifierType(Long id, FinancesDto.TypeFraisSaisie saisie) {
        TypeFrais type = type(id);
        String code = saisie.code().trim().toUpperCase();
        if (!type.getCode().equalsIgnoreCase(code) && typesFrais.existsByCodeIgnoreCase(code)) {
            throw new RegleMetierException("Un type de frais porte déjà le code " + code);
        }
        type.setCode(code);
        appliquer(type, saisie);
        return versVue(type);
    }

    public FinancesDto.TypeFraisVue activerType(Long id, boolean actif) {
        TypeFrais type = type(id);
        type.setActif(actif);
        return versVue(type);
    }

    // ---------- Grille tarifaire ----------

    @Transactional(readOnly = true)
    public List<FinancesDto.TarifVue> listerGrille(Long anneeId) {
        return grilles.findByAnneeScolaireId(anneeId).stream()
                .map(TarifService::versVue)
                .sorted((a, b) -> {
                    int parNiveau = cle(a).compareTo(cle(b));
                    return parNiveau != 0 ? parNiveau : a.typeFraisCode().compareTo(b.typeFraisCode());
                })
                .toList();
    }

    public FinancesDto.TarifVue ajouterTarif(Long anneeId, FinancesDto.TarifSaisie saisie) {
        GrilleTarifaire tarif = new GrilleTarifaire();
        tarif.setAnneeScolaire(annee(anneeId));
        appliquer(tarif, saisie);
        return versVue(grilles.save(tarif));
    }

    public FinancesDto.TarifVue modifierTarif(Long tarifId, FinancesDto.TarifSaisie saisie) {
        GrilleTarifaire tarif = grilles.findById(tarifId)
                .orElseThrow(() -> new IntrouvableException("Tarif introuvable : " + tarifId));
        appliquer(tarif, saisie);
        return versVue(tarif);
    }

    public void supprimerTarif(Long tarifId) {
        GrilleTarifaire tarif = grilles.findById(tarifId)
                .orElseThrow(() -> new IntrouvableException("Tarif introuvable : " + tarifId));
        grilles.delete(tarif);
    }

    /** Vide la grille d'une année, avant de la reconduire depuis une autre (SF-22). */
    public void viderGrille(Long anneeId) {
        grilles.deleteAll(grilles.findByAnneeScolaireId(anneeId));
    }

    /**
     * Reconduit la grille d'une autre année, avec une hausse facultative en
     * pourcentage (SF-22). Les tarifs visant une classe précise ne sont repris
     * que si une classe de même nom existe dans l'année de destination.
     */
    public List<FinancesDto.TarifVue> dupliquerGrille(Long anneeCibleId, FinancesDto.DuplicationGrille saisie) {
        AnneeScolaire cible = annee(anneeCibleId);
        if (grilles.countByAnneeScolaireId(anneeCibleId) > 0) {
            throw new RegleMetierException("La grille de " + cible.getLibelle()
                    + " contient déjà des tarifs : videz-la avant de la reconduire");
        }
        BigDecimal facteur = BigDecimal.ONE.add(
                Optional.ofNullable(saisie.haussePourcentage()).orElse(BigDecimal.ZERO)
                        .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));

        List<Classe> classesCible = classes.findByAnneeScolaireIdOrderByNom(anneeCibleId);
        List<GrilleTarifaire> copies = new ArrayList<>();
        for (GrilleTarifaire source : grilles.findByAnneeScolaireId(saisie.anneeSourceId())) {
            GrilleTarifaire copie = new GrilleTarifaire();
            copie.setAnneeScolaire(cible);
            copie.setTypeFrais(source.getTypeFrais());
            copie.setCategorie(source.getCategorie());
            copie.setMontant(source.getMontant().multiply(facteur).setScale(0, RoundingMode.HALF_UP));
            if (source.getNiveau() != null) {
                copie.setNiveau(source.getNiveau());
            } else {
                String nom = source.getClasse().getNom();
                Optional<Classe> equivalente = classesCible.stream()
                        .filter(c -> c.getNom().equalsIgnoreCase(nom))
                        .findFirst();
                if (equivalente.isEmpty()) {
                    continue;
                }
                copie.setClasse(equivalente.get());
            }
            copies.add(copie);
        }
        grilles.saveAll(copies);
        return listerGrille(anneeCibleId);
    }

    /**
     * Tarif applicable à un élève (SF-22) : classe + catégorie, puis classe + TOUS,
     * puis niveau + catégorie, puis niveau + TOUS. Vide si aucun tarif n'est défini.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> resoudreTarif(Long anneeId, Classe classe, CategorieEleve categorie,
                                              Long typeFraisId) {
        List<GrilleTarifaire> candidats = grilles.findByAnneeScolaireId(anneeId).stream()
                .filter(g -> g.getTypeFrais().getId().equals(typeFraisId))
                .toList();
        CategorieTarif exacte = CategorieTarif.valueOf(categorie.name());

        return premier(candidats, g -> estClasse(g, classe) && g.getCategorie() == exacte)
                .or(() -> premier(candidats, g -> estClasse(g, classe) && g.getCategorie() == CategorieTarif.TOUS))
                .or(() -> premier(candidats, g -> estNiveau(g, classe) && g.getCategorie() == exacte))
                .or(() -> premier(candidats, g -> estNiveau(g, classe) && g.getCategorie() == CategorieTarif.TOUS))
                .map(GrilleTarifaire::getMontant);
    }

    // ---------- Outils ----------

    private static Optional<GrilleTarifaire> premier(List<GrilleTarifaire> candidats,
                                                     java.util.function.Predicate<GrilleTarifaire> filtre) {
        return candidats.stream().filter(filtre).findFirst();
    }

    private static boolean estClasse(GrilleTarifaire tarif, Classe classe) {
        return tarif.getClasse() != null && tarif.getClasse().getId().equals(classe.getId());
    }

    private static boolean estNiveau(GrilleTarifaire tarif, Classe classe) {
        return tarif.getNiveau() != null && tarif.getNiveau().getId().equals(classe.getNiveau().getId());
    }

    private static String cle(FinancesDto.TarifVue vue) {
        return (vue.niveauCode() != null ? vue.niveauCode() : vue.classeNom()) + "/" + vue.categorie();
    }

    private void appliquer(TypeFrais type, FinancesDto.TypeFraisSaisie saisie) {
        type.setLibelle(saisie.libelle().trim());
        type.setObligatoire(saisie.obligatoire());
        type.setFractionnable(saisie.fractionnable());
        type.setPrioriteImputation(saisie.prioriteImputation());
    }

    private void appliquer(GrilleTarifaire tarif, FinancesDto.TarifSaisie saisie) {
        if ((saisie.niveauId() == null) == (saisie.classeId() == null)) {
            throw new SaisieInvalideException("Un tarif vise soit un niveau, soit une classe précise");
        }
        TypeFrais type = type(saisie.typeFraisId());
        tarif.setTypeFrais(type);
        tarif.setCategorie(saisie.categorie());
        tarif.setMontant(saisie.montant().setScale(0, RoundingMode.HALF_UP));
        tarif.setNiveau(saisie.niveauId() == null ? null : niveaux.findById(saisie.niveauId())
                .orElseThrow(() -> new IntrouvableException("Niveau introuvable : " + saisie.niveauId())));
        tarif.setClasse(saisie.classeId() == null ? null : classes.findById(saisie.classeId())
                .orElseThrow(() -> new IntrouvableException("Classe introuvable : " + saisie.classeId())));
    }

    private TypeFrais type(Long id) {
        return typesFrais.findById(id)
                .orElseThrow(() -> new IntrouvableException("Type de frais introuvable : " + id));
    }

    private AnneeScolaire annee(Long id) {
        return annees.findById(id)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + id));
    }

    private static FinancesDto.TypeFraisVue versVue(TypeFrais type) {
        return new FinancesDto.TypeFraisVue(type.getId(), type.getCode(), type.getLibelle(),
                type.isObligatoire(), type.isFractionnable(), type.getPrioriteImputation(), type.isActif());
    }

    private static FinancesDto.TarifVue versVue(GrilleTarifaire tarif) {
        Niveau niveau = tarif.getNiveau();
        Classe classe = tarif.getClasse();
        return new FinancesDto.TarifVue(tarif.getId(), tarif.getTypeFrais().getId(),
                tarif.getTypeFrais().getCode(), tarif.getTypeFrais().getLibelle(),
                niveau == null ? null : niveau.getId(), niveau == null ? null : niveau.getCode(),
                classe == null ? null : classe.getId(), classe == null ? null : classe.getNom(),
                tarif.getCategorie(), tarif.getMontant());
    }
}
