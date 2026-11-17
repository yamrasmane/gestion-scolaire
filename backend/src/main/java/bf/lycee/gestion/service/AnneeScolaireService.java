package bf.lycee.gestion.service;

import bf.lycee.gestion.domain.AnneeScolaire;
import bf.lycee.gestion.domain.audit.Revision;
import bf.lycee.gestion.repository.AnneeScolaireRepository;
import bf.lycee.gestion.web.AnneeScolaireDto;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des années scolaires (SF-01). */
@Service
@Transactional
public class AnneeScolaireService {

    private static final BigDecimal POIDS_PAR_DEFAUT = new BigDecimal("2");

    private final AnneeScolaireRepository repository;
    private final EntityManager entityManager;

    public AnneeScolaireService(AnneeScolaireRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AnneeScolaire> lister() {
        return repository.findAllByOrderByDateDebutDesc();
    }

    @Transactional(readOnly = true)
    public AnneeScolaire active() {
        return repository.findByActifTrue()
                .orElseThrow(() -> new IntrouvableException("Aucune année scolaire active"));
    }

    public AnneeScolaire creer(AnneeScolaireDto.Creation saisie) {
        if (!saisie.dateFin().isAfter(saisie.dateDebut())) {
            throw new RegleMetierException("La date de fin doit être postérieure à la date de début");
        }
        if (repository.existsByLibelle(saisie.libelle())) {
            throw new RegleMetierException("L'année scolaire " + saisie.libelle() + " existe déjà");
        }
        AnneeScolaire annee = new AnneeScolaire();
        annee.setLibelle(saisie.libelle());
        annee.setDateDebut(saisie.dateDebut());
        annee.setDateFin(saisie.dateFin());
        annee.setAvecCompositionDefaut(saisie.avecCompositionDefaut() == null || saisie.avecCompositionDefaut());
        annee.setPoidsCompositionDefaut(
                saisie.poidsCompositionDefaut() != null ? saisie.poidsCompositionDefaut() : POIDS_PAR_DEFAUT);
        annee.setBloquerBulletinImpaye(Boolean.TRUE.equals(saisie.bloquerBulletinImpaye()));
        annee.setActif(false);
        return repository.save(annee);
    }

    /** Active une année et désactive la précédente : une seule année active à la fois. */
    public AnneeScolaire activer(UUID id) {
        AnneeScolaire annee = repository.findById(id)
                .orElseThrow(() -> new IntrouvableException("Année scolaire introuvable : " + id));
        if (annee.isActif()) {
            return annee;
        }
        repository.findByActifTrue().ifPresent(precedente -> {
            precedente.setActif(false);
            // L'index unique partiel impose de désactiver avant d'activer
            repository.saveAndFlush(precedente);
        });
        annee.setActif(true);
        return repository.saveAndFlush(annee);
    }

    /** Piste d'audit d'une année scolaire (SF-20), de la plus ancienne révision à la plus récente. */
    @Transactional(readOnly = true)
    public List<AnneeScolaireDto.Historique> historique(UUID id) {
        List<?> lignes = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(AnneeScolaire.class, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        List<AnneeScolaireDto.Historique> historique = new ArrayList<>();
        for (Object ligne : lignes) {
            Object[] triplet = (Object[]) ligne;
            AnneeScolaire etat = (AnneeScolaire) triplet[0];
            Revision revision = (Revision) triplet[1];
            RevisionType type = (RevisionType) triplet[2];
            historique.add(new AnneeScolaireDto.Historique(
                    revision.getId(),
                    Instant.ofEpochMilli(revision.getTimestamp()),
                    revision.getUsername(),
                    revision.getIpAddress(),
                    switch (type) {
                        case ADD -> "CREATION";
                        case MOD -> "MODIFICATION";
                        case DEL -> "SUPPRESSION";
                    },
                    etat.getLibelle(),
                    etat.isActif()));
        }
        return historique;
    }
}
