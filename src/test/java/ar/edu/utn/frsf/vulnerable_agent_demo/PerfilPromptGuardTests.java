package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.promptguard.spring.PromptGuardChatClientCustomizer;
import ar.edu.utn.frsf.promptguard.spring.PromptGuardRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Con el perfil `promptguard` el starter debe quedar instanciado: el guard envuelve las herramientas del agente.
// No hace llamadas de red.
@SpringBootTest(properties = {"app.vectorstore=simple", "spring.ai.model.embedding=none", "GROQ_API_KEY=test"})
@ActiveProfiles("promptguard")
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class PerfilPromptGuardTests {

    @Autowired
    ApplicationContext contexto;

    @Test
    void elPerfilActivaElGuardSobreLasHerramientas() {
        assertThat(contexto.getBeansOfType(PromptGuardRuntime.class)).hasSize(1);
        assertThat(contexto.getBeansOfType(PromptGuardChatClientCustomizer.class)).hasSize(1);
    }
}
