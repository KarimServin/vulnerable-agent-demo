package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Un perfil de agente inválido (por ejemplo "limpio", que es un modo del corpus) debe impedir el arranque.
class PerfilInvalidoTests {

    @Test
    void unPerfilDesconocidoNoArranca() {
        assertThatThrownBy(() -> {
            // Argumentos de línea de comandos: tienen prioridad sobre application.yaml.
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VulnerableAgentDemoApplication.class,
                    VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
                    .web(WebApplicationType.NONE)
                    .run("--app.vectorstore=simple", "--spring.ai.model.embedding=none", "--GROQ_API_KEY=test",
                            "--app.agent.profile=limpio")) {
                // no debería llegar acá
            }
        }).hasStackTraceContaining("Perfil del agente desconocido: 'limpio'");
    }
}
