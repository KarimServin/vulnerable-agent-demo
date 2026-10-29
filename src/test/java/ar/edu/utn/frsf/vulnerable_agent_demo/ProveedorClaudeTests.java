package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// Con el perfil `claude` el chat debe ir a Anthropic y la traza debe nombrar el modelo.
// No hace ninguna llamada de red.
@SpringBootTest(properties = {
        "app.vectorstore=simple",
        "spring.ai.model.embedding=none",
        "spring.profiles.active=claude",
        "ANTHROPIC_API_KEY=test"
})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class ProveedorClaudeTests {

    @Autowired
    SupportAgentService agente;

    @Test
    void elPerfilClaudeUsaAnthropicYRegistraElModelo() {
        assertThat(agente.getModelo()).startsWith("anthropic:claude-");
    }
}
