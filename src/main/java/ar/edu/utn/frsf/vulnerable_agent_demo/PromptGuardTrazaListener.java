package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.promptguard.spring.EventoAuditoria;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Copia cada decisión de PromptGuard a la traza del experimento. En modo sombra permite ver qué habría
 * bloqueado el guard aunque la herramienta se haya ejecutado.
 */
@Component
class PromptGuardTrazaListener {

    private final ExperimentTraceService traceService;

    PromptGuardTrazaListener(ExperimentTraceService traceService) {
        this.traceService = traceService;
    }

    @EventListener
    void alDecidir(EventoAuditoria evento) {
        traceService.recordDecision(evento.herramienta() + " decision=" + evento.decision()
                + " regla=" + evento.regla() + " modo=" + evento.modo() + " ejecutada=" + evento.ejecutada()
                + " motivo=" + evento.motivo());
    }
}
