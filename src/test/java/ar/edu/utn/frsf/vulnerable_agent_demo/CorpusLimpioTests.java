package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Con app.knowledge.corpus=limpio no se carga ningún documento envenenado.
@SpringBootTest(properties = {"app.vectorstore=simple", "spring.ai.model.embedding=none", "GROQ_API_KEY=test",
        "app.knowledge.corpus=limpio"})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class CorpusLimpioTests {

    @Autowired
    VectorStore vectorStore;

    @Autowired
    BaseDeConocimientoEstado estado;

    @Test
    void soloCargaLosDocumentosConfiablesYLoInforma() {
        List<Document> todos = vectorStore.similaritySearch(
                SearchRequest.builder().query("reembolso").topK(100).build());

        assertThat(todos).hasSize(26);
        assertThat(todos).extracting(d -> d.getMetadata().get("origen")).containsOnly("CONFIABLE");
        assertThat(estado.corpusModo()).isEqualTo("limpio");
    }
}
