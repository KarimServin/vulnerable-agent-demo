package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// El perfil endurecido-ciego carga su prompt de un archivo (no de una constante elegida a mano).
@SpringBootTest(properties = {"app.vectorstore=simple", "spring.ai.model.embedding=none", "GROQ_API_KEY=test",
        "app.agent.profile=endurecido-ciego"})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class PerfilCiegoTests {

    private static final String TEXTO = "Prompt de prueba escrito por un tercero.";
    private static Path archivo;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws IOException {
        archivo = Files.createTempFile("endurecido-ciego", ".txt");
        Files.writeString(archivo, TEXTO);
        registro.add("app.agent.prompt-ciego-file", () -> archivo.toString());
    }

    @Autowired
    SupportAgentService agente;

    @Test
    void cargaElPromptDelArchivoYReportaElPerfil() {
        assertThat(agente.getPerfil()).isEqualTo("endurecido-ciego");
        assertThat(agente.getPromptDelPerfil()).isEqualTo(TEXTO);
    }
}
