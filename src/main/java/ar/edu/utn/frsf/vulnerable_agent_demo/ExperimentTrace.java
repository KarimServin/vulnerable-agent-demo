package ar.edu.utn.frsf.vulnerable_agent_demo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ExperimentTrace {

    private final String id = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();
    private final String pregunta;
    private final List<ToolInvocation> herramientas = new CopyOnWriteArrayList<>();
    private final String perfil;
    private final String modelo;
    private final List<DocumentoRecuperado> documentosRecuperados = new CopyOnWriteArrayList<>();
    private final List<String> decisiones = new CopyOnWriteArrayList<>();
    private String respuesta;
    private long latenciaMs;

    public ExperimentTrace(String pregunta, String perfil, String modelo) {
        this.pregunta = pregunta;
        this.perfil = perfil;
        this.modelo = modelo;
    }

    public void addDocumentoRecuperado(String fuente, String origen, String tipo) {
        DocumentoRecuperado documento = new DocumentoRecuperado(fuente, origen, tipo);
        // El advisor de RAG vuelve a buscar en cada iteración del bucle de herramientas.
        if (!documentosRecuperados.contains(documento)) {
            documentosRecuperados.add(documento);
        }
    }

    public String getPerfil() {
        return perfil;
    }

    public String getModelo() {
        return modelo;
    }

    public List<DocumentoRecuperado> getDocumentosRecuperados() {
        return documentosRecuperados;
    }

    public void complete(String respuesta, long latenciaMs) {
        this.respuesta = respuesta;
        this.latenciaMs = latenciaMs;
    }

    public void addToolInvocation(String herramienta, String argumentos, String resultado) {
        herramientas.add(new ToolInvocation(Instant.now(), herramienta, argumentos, resultado));
    }

    public String getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public String getPregunta() {
        return pregunta;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public long getLatenciaMs() {
        return latenciaMs;
    }

    public List<ToolInvocation> getHerramientas() {
        return herramientas;
    }

    /** Decisiones de PromptGuard sobre las llamadas de esta consulta (vacío en el baseline). */
    public List<String> getDecisiones() {
        return decisiones;
    }

    public void addDecision(String decision) {
        decisiones.add(decision);
    }

    public record DocumentoRecuperado(String fuente, String origen, String tipo) {
    }

    public record ToolInvocation(Instant timestamp, String herramienta, String argumentos, String resultado) {
    }
}
