package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.vulnerable_agent_demo.experimento.IngestaController;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"app.vectorstore=simple", "spring.ai.model.embedding=none", "GROQ_API_KEY=test"})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class IngestaTests {

    @Autowired
    IngestaController ingesta;

    @Autowired
    VectorStore vectorStore;

    private int total() {
        return vectorStore.similaritySearch(SearchRequest.builder().query("reembolso").topK(1000).build()).size();
    }

    @Test
    void agregaYRetiraSoloLoInyectado() {
        int antes = total();

        ingesta.agregar(List.of(
                new IngestaController.DocumentoEntrada("a.txt", "Texto de prueba uno", "CONFIABLE", "sintetico"),
                new IngestaController.DocumentoEntrada("b.txt", "Texto de prueba dos", "CONFIABLE", "sintetico")));
        assertThat(total()).isEqualTo(antes + 2);

        assertThat(ingesta.retirarTodo()).containsEntry("retirados", 2);
        assertThat(total()).isEqualTo(antes);
    }
}
