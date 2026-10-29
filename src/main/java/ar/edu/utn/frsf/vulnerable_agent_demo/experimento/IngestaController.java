package ar.edu.utn.frsf.vulnerable_agent_demo.experimento;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Canal de ingesta del experimento: agrega documentos al RAG en tiempo de ejecución y los retira.
 * Lo usa el barrido de dilución (documentos legítimos sintéticos) para medir cuánto compite el veneno
 * en una base más grande. No filtra ni valida nada: simula un pipeline que indexa lo que recibe.
 */
@RestController
@RequestMapping("/experiment/ingesta")
public class IngestaController {

    public record DocumentoEntrada(String fuente, String texto, String origen, String tipo) {
    }

    private static final int LOTE = 100;

    private final VectorStore vectorStore;
    private final List<String> idsInyectados = new CopyOnWriteArrayList<>();

    public IngestaController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostMapping
    public Map<String, Integer> agregar(@RequestBody List<DocumentoEntrada> entrada) {
        for (int i = 0; i < entrada.size(); i += LOTE) {
            List<Document> lote = new ArrayList<>();
            for (DocumentoEntrada d : entrada.subList(i, Math.min(i + LOTE, entrada.size()))) {
                String id = UUID.randomUUID().toString();
                lote.add(new Document(id, d.texto(), Map.of("origen", d.origen(), "fuente", d.fuente(),
                        "tipo", d.tipo() == null ? "inyectado" : d.tipo())));
                idsInyectados.add(id);
            }
            vectorStore.add(lote);
        }
        return Map.of("agregados", entrada.size(), "totalInyectados", idsInyectados.size());
    }

    @DeleteMapping
    public Map<String, Integer> retirarTodo() {
        List<String> ids = new ArrayList<>(idsInyectados);
        for (int i = 0; i < ids.size(); i += LOTE) {
            vectorStore.delete(ids.subList(i, Math.min(i + LOTE, ids.size())));
        }
        idsInyectados.clear();
        return Map.of("retirados", ids.size());
    }
}
