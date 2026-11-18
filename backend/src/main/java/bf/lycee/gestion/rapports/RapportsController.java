package bf.lycee.gestion.rapports;

import bf.lycee.gestion.security.Roles;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Édition des documents. Chaque adresse accepte :
 * <ul>
 *   <li>{@code format} : pdf (défaut), xlsx, docx, html ou csv ;</li>
 *   <li>{@code modele=true} : renvoie, au lieu du document, le modèle de
 *       données JSON à brancher dans Jaspersoft Studio pour redessiner le
 *       fichier .jrxml correspondant.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/rapports")
public class RapportsController {

    private final DocumentsLycee documents;

    public RapportsController(DocumentsLycee documents) {
        this.documents = documents;
    }

    @GetMapping("/recus/{paiementId}")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public ResponseEntity<byte[]> recu(@PathVariable UUID paiementId,
                                       @RequestParam(defaultValue = "false") boolean duplicata,
                                       @RequestParam(defaultValue = "pdf") String format,
                                       @RequestParam(defaultValue = "false") boolean modele) {
        return repondre(documents.recu(paiementId, duplicata), format, modele);
    }

    @GetMapping("/bulletins/{inscriptionId}/trimestres/{trimestreId}")
    public ResponseEntity<byte[]> bulletin(@PathVariable UUID inscriptionId,
                                           @PathVariable UUID trimestreId,
                                           @RequestParam(defaultValue = "pdf") String format,
                                           @RequestParam(defaultValue = "false") boolean modele) {
        return repondre(documents.bulletin(inscriptionId, trimestreId), format, modele);
    }

    @GetMapping("/impayes/{anneeId}")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public ResponseEntity<byte[]> impayes(
            @PathVariable UUID anneeId,
            @RequestParam(required = false) UUID classeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate limite,
            @RequestParam(defaultValue = "") String recherche,
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(defaultValue = "false") boolean modele) {
        return repondre(documents.impayes(anneeId, classeId, limite, recherche), format, modele);
    }

    @GetMapping("/journal-caisse")
    @PreAuthorize(Roles.FINANCES_LECTURE)
    public ResponseEntity<byte[]> journal(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "false") boolean modele) {
        return repondre(documents.journal(journee), format, modele);
    }

    /** Document ou modèle de données, selon ce qui est demandé. */
    private ResponseEntity<byte[]> repondre(DocumentsLycee.Composition composition, String format,
                                            boolean modele) {
        if (modele) {
            byte[] json = documents.modele(composition).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(composition.rapport() + "-modele.json").build().toString())
                    .body(json);
        }
        ServiceRapports.Document document =
                documents.produire(composition, FormatRapport.depuis(format));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, document.format().typeMime())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .inline().filename(document.nomFichier()).build().toString())
                .body(document.contenu());
    }
}
