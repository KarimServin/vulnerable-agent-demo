package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carga la base de conocimiento desde {@code classpath:knowledge/}.
 *
 * <ul>
 *   <li>{@code knowledge/confiable/*.txt}: conocimiento legítimo (origen CONFIABLE).</li>
 *   <li>{@code knowledge/no-confiable/*.txt}: fuentes envenenadas (origen NO_CONFIABLE).
 *       El prefijo del nombre antes de {@code __} es el tipo de ataque.</li>
 * </ul>
 *
 * Cada documento conserva {@code origen}, {@code fuente} y {@code tipo} como metadatos.
 */
@Configuration
public class KnowledgeBaseLoader {

    static final String ORIGEN_CONFIABLE = "CONFIABLE";
    static final String ORIGEN_NO_CONFIABLE = "NO_CONFIABLE";

    // Valores por defecto de Spring AI, explícitos porque la baja de la colección usa la API REST.
    private static final String TENANT = "SpringAiTenant";
    private static final String DATABASE = "SpringAiDatabase";

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore", havingValue = "chroma", matchIfMissing = true)
    VectorStore chromaVectorStore(EmbeddingModel embeddingModel,
                                  @Value("${app.chroma.url:http://localhost:8000}") String url,
                                  @Value("${app.chroma.collection:vulnerable-agent-kb}") String collection) {
        // Cada arranque parte de una colección nueva. Borrar y volver a cargar documentos
        // sobre la misma colección deja vectores viejos en el índice HNSW de Chroma y las
        // búsquedas devuelven vecinos incorrectos (casi siempre los mismos documentos).
        try {
            RestClient.builder().baseUrl(url).build().delete()
                    .uri("/api/v2/tenants/{t}/databases/{d}/collections/{c}", TENANT, DATABASE, collection)
                    .retrieve().toBodilessEntity();
        }
        catch (RestClientResponseException ignorada) {
            // 404: tenant, base o colección todavía no existen
        }

        ChromaApi api = ChromaApi.builder()
                .baseUrl(url)
                .restClientBuilder(RestClient.builder())
                .build();
        return ChromaVectorStore.builder(api, embeddingModel)
                .tenantName(TENANT)
                .databaseName(DATABASE)
                .collectionName(collection)
                .initializeSchema(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.vectorstore", havingValue = "simple")
    VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    CommandLineRunner cargarDocumentos(VectorStore vectorStore, BaseDeConocimientoEstado estado,
                                       @Value("${app.knowledge.corpus:completo}") String corpus,
                                       @Value("${app.knowledge.extra:}") String extra) {
        return args -> {
            List<Document> documentos = new ArrayList<>();
            documentos.addAll(leer("classpath:knowledge/confiable/*.txt", ORIGEN_CONFIABLE));
            // `limpio`: sin documentos envenenados. Mide el comportamiento del agente sin ningún atacante
            // (acciones no pedidas, argumentos equivocados, utilidad).
            if (!"limpio".equals(corpus)) {
                documentos.addAll(leer("classpath:knowledge/no-confiable/*.txt", ORIGEN_NO_CONFIABLE));
                // `politica`: documentos de un atacante que conoce la política de PromptGuard (no forman parte del
                // corpus base, así su huella no cambia salvo que se pida).
                if ("politica".equals(extra)) {
                    documentos.addAll(leer("classpath:knowledge/conoce-politica/*.txt", ORIGEN_NO_CONFIABLE));
                }
            }

            vectorStore.add(documentos);
            estado.marcarLista(huella(documentos), "limpio".equals(corpus) ? "limpio" : "completo");

            long noConfiables = documentos.stream()
                    .filter(d -> ORIGEN_NO_CONFIABLE.equals(d.getMetadata().get("origen")))
                    .count();
            System.out.println("=== BASE DE CONOCIMIENTO CARGADA ===");
            System.out.println("Documentos: " + documentos.size()
                    + " (confiables: " + (documentos.size() - noConfiables)
                    + ", no confiables: " + noConfiables + ")");
            System.out.println("====================================");
        };
    }

    /** SHA-256 (12 hex) sobre origen, fuente y texto de cada documento, en orden estable. */
    private static String huella(List<Document> documentos) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            documentos.stream()
                    .sorted(java.util.Comparator.comparing(d -> d.getMetadata().get("origen") + "/" + d.getMetadata().get("fuente")))
                    .forEach(d -> digest.update((d.getMetadata().get("origen") + "/" + d.getMetadata().get("fuente")
                            + "|" + d.getText() + "|").getBytes(StandardCharsets.UTF_8)));
            return java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        }
        catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Document> leer(String patron, String origen) throws IOException {
        Resource[] recursos = new PathMatchingResourcePatternResolver().getResources(patron);
        List<Document> documentos = new ArrayList<>();
        for (Resource recurso : recursos) {
            String fuente = recurso.getFilename();
            String texto = recurso.getContentAsString(StandardCharsets.UTF_8);
            Map<String, Object> metadatos = new LinkedHashMap<>();
            metadatos.put("origen", origen);
            metadatos.put("fuente", fuente);
            metadatos.put("tipo", tipo(origen, fuente));
            // ID determinista: reiniciar no duplica documentos.
            String id = UUID.nameUUIDFromBytes((origen + "/" + fuente).getBytes(StandardCharsets.UTF_8)).toString();
            documentos.add(new Document(id, texto, metadatos));
        }
        return documentos;
    }

    private static String tipo(String origen, String fuente) {
        if (ORIGEN_CONFIABLE.equals(origen)) {
            return "LEGITIMO";
        }
        int separador = fuente.indexOf("__");
        return separador > 0 ? fuente.substring(0, separador) : "SIN_CLASIFICAR";
    }
}
