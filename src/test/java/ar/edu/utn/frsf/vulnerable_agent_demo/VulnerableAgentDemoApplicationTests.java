package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Las pruebas no dependen de Chroma, del modelo ONNX ni del modelo de chat.
@SpringBootTest(properties = {
        "app.vectorstore=simple",
        "spring.ai.model.embedding=none",
        "GROQ_API_KEY=test"
})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class VulnerableAgentDemoApplicationTests {

    @Autowired
    VectorStore vectorStore;

    @Test
    void contextLoads() {
    }

    @Test
    void baseDeConocimientoSeparaFuentesConfiablesYNoConfiables() {
        List<Document> todos = vectorStore.similaritySearch(
                SearchRequest.builder().query("reembolso").topK(100).build());

        assertThat(todos).hasSize(41);
        assertThat(todos).extracting(d -> d.getMetadata().get("origen"))
                .containsOnly("CONFIABLE", "NO_CONFIABLE");
        assertThat(todos.stream().filter(d -> "NO_CONFIABLE".equals(d.getMetadata().get("origen"))))
                .hasSize(15)
                .allSatisfy(d -> assertThat(d.getMetadata().get("tipo")).isNotEqualTo("LEGITIMO"));
    }

    @TestConfiguration
    static class TestEmbeddingsConfiguration {

        @Bean
        EmbeddingModel embeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    return new EmbeddingResponse(request.getInstructions().stream()
                            .map(texto -> new Embedding(vector(texto), 0))
                            .toList());
                }

                @Override
                public float[] embed(Document document) {
                    return vector(document.getText());
                }

                @Override
                public int dimensions() {
                    return 32;
                }

                private float[] vector(String texto) {
                    float[] v = new float[32];
                    for (String token : texto.toLowerCase().split("\\W+")) {
                        if (!token.isBlank()) {
                            v[Math.floorMod(token.hashCode(), 32)] += 1.0f;
                        }
                    }
                    return v;
                }
            };
        }
    }
}
