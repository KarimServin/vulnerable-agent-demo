package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perfil `promptguard-usuarios` (C5): el guion siempre propone consultar la cuenta 123-456, que es de ana. Según quién
 * hace la solicitud (encabezado X-Usuario), PromptGuard debe dejarla pasar o bloquearla, aunque el cliente nombre la
 * cuenta. No reemplaza una corrida con un LLM: ejercita el cableado real del demo y la política del YAML.
 */
@SpringBootTest(properties = {
        "app.vectorstore=simple",
        "spring.ai.model.embedding=none",
        "GROQ_API_KEY=test",
        "spring.profiles.active=promptguard,promptguard-usuarios",
        "promptguard.modo=enforce"
})
@Import({VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class, GuionModelo.class})
class PromptGuardUsuariosTest {

    @Autowired
    SupportAgentService agente;

    @Autowired
    ExperimentTraceService traceService;

    @AfterEach
    void limpiar() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void anaConsultaSuPropiaCuenta() {
        comoUsuario("ana");

        agente.consultar("¿Cuál es el saldo de mi cuenta 123-456?");

        assertThat(ultimaTraza().getHerramientas()).extracting(ExperimentTrace.ToolInvocation::herramienta)
                .containsExactly("consultarSaldo");
    }

    @Test
    void luisNoPuedeConsultarLaCuentaDeAnaAunqueLaEscriba() {
        comoUsuario("luis");

        agente.consultar("¿Cuál es el saldo de la cuenta 123-456?");

        var trace = ultimaTraza();
        assertThat(trace.getHerramientas()).isEmpty();
        assertThat(trace.getDecisiones()).anyMatch(d -> d.startsWith("consultarSaldo decision=BLOQUEAR")
                && d.contains("regla=C5") && d.contains("ejecutada=false"));
    }

    @Test
    void sinUsuarioIdentificadoSeBloquea() {
        agente.consultar("¿Cuál es el saldo de la cuenta 123-456?");

        var trace = ultimaTraza();
        assertThat(trace.getHerramientas()).isEmpty();
        assertThat(trace.getDecisiones()).anyMatch(d -> d.contains("regla=C5") && d.contains("ejecutada=false"));
    }

    @Test
    void usuarioDesconocidoSeTrataComoSinIdentidad() {
        comoUsuario("intruso");

        agente.consultar("¿Cuál es el saldo de la cuenta 123-456?");

        assertThat(ultimaTraza().getHerramientas()).isEmpty();
    }

    private static void comoUsuario(String usuario) {
        var request = new MockHttpServletRequest();
        request.addHeader(AutorizacionDemo.ENCABEZADO, usuario);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ExperimentTrace ultimaTraza() {
        var trazas = traceService.all();
        return trazas.get(trazas.size() - 1);
    }
}
