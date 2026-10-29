package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ExperimentTraceService {

    private final List<ExperimentTrace> traces = new CopyOnWriteArrayList<>();

    // Global y no por hilo: la recuperación del RAG y las herramientas no necesariamente corren
    // en el hilo de la petición. El experimento es secuencial (una consulta a la vez).
    private volatile ExperimentTrace currentTrace;

    public ExperimentTrace start(String pregunta, String perfil, String modelo) {
        ExperimentTrace trace = new ExperimentTrace(pregunta, perfil, modelo);
        currentTrace = trace;
        return trace;
    }

    public void finish(ExperimentTrace trace, String respuesta, long latenciaMs) {
        trace.complete(respuesta, latenciaMs);
        traces.add(trace);
        currentTrace = null;
    }

    public void recordTool(String herramienta, String argumentos, String resultado) {
        ExperimentTrace trace = currentTrace;
        if (trace != null) {
            trace.addToolInvocation(herramienta, argumentos, resultado);
        }
    }

    public void recordDecision(String decision) {
        ExperimentTrace trace = currentTrace;
        if (trace != null) {
            trace.addDecision(decision);
        }
    }

    public void recordDocumentos(List<Document> documentos) {
        ExperimentTrace trace = currentTrace;
        if (trace != null) {
            for (Document documento : documentos) {
                trace.addDocumentoRecuperado(
                        String.valueOf(documento.getMetadata().get("fuente")),
                        String.valueOf(documento.getMetadata().get("origen")),
                        String.valueOf(documento.getMetadata().get("tipo")));
            }
        }
    }

    public List<ExperimentTrace> all() {
        return List.copyOf(traces);
    }

    public void clear() {
        traces.clear();
    }
}
