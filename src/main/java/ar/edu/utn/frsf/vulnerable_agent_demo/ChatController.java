package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private final SupportAgentService supportAgentService;
    private final ExperimentTraceService traceService;

    private final VectorStore vectorStore;
    private final BaseDeConocimientoEstado baseEstado;
    private final int topK;

    public ChatController(SupportAgentService supportAgentService, ExperimentTraceService traceService,
                          VectorStore vectorStore, BaseDeConocimientoEstado baseEstado,
                          @Value("${app.rag.top-k:4}") int topK) {
        this.supportAgentService = supportAgentService;
        this.traceService = traceService;
        this.vectorStore = vectorStore;
        this.baseEstado = baseEstado;
        this.topK = topK;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String pregunta) {
        return supportAgentService.consultar(pregunta);
    }

    public record MensajePrevio(String rol, String texto) {
    }

    public record Conversacion(List<MensajePrevio> historial, String pregunta) {
    }

    /** Segundo turno (o siguientes) de una conversación; sin estado en el servidor: el historial viaja en el pedido. */
    @PostMapping("/chat/conversacion")
    public String conversacion(@RequestBody Conversacion conversacion) {
        List<Message> previos = conversacion.historial().stream()
                .<Message>map(m -> "assistant".equals(m.rol())
                        ? new AssistantMessage(m.texto())
                        : new UserMessage(m.texto()))
                .toList();
        return supportAgentService.consultar(conversacion.pregunta(), previos);
    }

    /** Qué recuperaría el RAG para una pregunta, sin llamar al modelo (mismos topK y umbral que el advisor). */
    @GetMapping("/experiment/retrieval")
    public List<Map<String, Object>> retrieval(@RequestParam String pregunta,
                                              @RequestParam(defaultValue = "0") int k) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(pregunta).topK(k > 0 ? k : topK).build()).stream()
                .map(d -> Map.<String, Object>of(
                        "fuente", String.valueOf(d.getMetadata().get("fuente")),
                        "origen", String.valueOf(d.getMetadata().get("origen")),
                        "score", d.getScore() == null ? "" : d.getScore()))
                .toList();
    }

    @GetMapping("/experiment/config")
    public Map<String, String> config() {
        return Map.of("perfil", supportAgentService.getPerfil(), "modelo", supportAgentService.getModelo(),
                "baseLista", String.valueOf(baseEstado.estaLista()),
                "corpus", baseEstado.corpusHash(), "corpusModo", baseEstado.corpusModo());
    }

    @GetMapping("/experiment/traces")
    public List<ExperimentTrace> traces() {
        return traceService.all();
    }

    @DeleteMapping("/experiment/traces")
    public void clearTraces() {
        traceService.clear();
    }
}
