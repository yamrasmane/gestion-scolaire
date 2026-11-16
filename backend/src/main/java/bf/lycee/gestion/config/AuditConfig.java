package bf.lycee.gestion.config;

import bf.lycee.gestion.security.UtilisateurCourant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Alimente created_by / updated_by à partir du jeton Keycloak (SF-19). */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(UtilisateurCourant.nomUtilisateur().orElse(UtilisateurCourant.SYSTEME));
    }
}
