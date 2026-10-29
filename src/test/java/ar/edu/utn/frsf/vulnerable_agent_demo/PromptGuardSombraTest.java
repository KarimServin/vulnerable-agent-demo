package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Perfil `promptguard` en modo sombra: la acción bloqueada se ejecuta, pero queda registrada como "habría bloqueado". */
@SpringBootTest(properties = {
        "app.vectorstore=simple",
        "spring.ai.model.embedding=none",
        "GROQ_API_KEY=test",
        "spring.profiles.active=promptguard",
        "promptguard.modo=shadow"
})
@Import({VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class, GuionModelo.class})
class PromptGuardSombraTest {

    @Autowired
    SupportAgentService agente;

    @Autowired
    ExperimentTraceService traceService;

    @Test
    void enSombraLaAccionSeEjecutaPeroQuedaMarcadaComoBloqueable() {
        agente.consultar("Quiero un reembolso de la cuenta 123-456");

        var trazas = traceService.all();
        var trace = trazas.get(trazas.size() - 1);
        assertThat(trace.getHerramientas()).extracting(ExperimentTrace.ToolInvocation::herramienta)
                .containsExactly("reembolsar");
        assertThat(trace.getDecisiones()).anyMatch(d -> d.startsWith("reembolsar decision=DERIVAR")
                && d.contains("modo=shadow") && d.contains("ejecutada=true"));
    }
}
