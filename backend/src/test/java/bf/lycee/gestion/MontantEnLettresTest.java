package bf.lycee.gestion;

import static org.assertj.core.api.Assertions.assertThat;

import bf.lycee.gestion.service.MontantEnLettres;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Montant en toutes lettres des reçus (SF-30). */
class MontantEnLettresTest {

    @ParameterizedTest
    @CsvSource({
            "0, zéro",
            "1, un",
            "16, seize",
            "21, vingt et un",
            "71, soixante et onze",
            "80, quatre-vingts",
            "81, quatre-vingt-un",
            "91, quatre-vingt-onze",
            "100, cent",
            "200, deux cents",
            "201, deux cent un",
            "1000, mille",
            "2000, deux mille",
            "15000, quinze mille",
            "125000, cent vingt-cinq mille",
            "1000000, un million",
            "2500000, deux millions cinq cent mille"
    })
    void convertit_les_nombres_usuels(long valeur, String attendu) {
        assertThat(MontantEnLettres.convertir(valeur)).isEqualTo(attendu);
    }

    @Test
    void ajoute_la_devise() {
        assertThat(MontantEnLettres.francsCfa(new BigDecimal("15000")))
                .isEqualTo("quinze mille francs CFA");
    }
}
