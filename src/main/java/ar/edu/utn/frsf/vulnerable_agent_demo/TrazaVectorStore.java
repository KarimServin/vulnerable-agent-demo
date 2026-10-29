package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Optional;

/**
 * Envuelve el VectorStore del advisor de RAG y registra en la traza qué documentos
 * se recuperaron, en el momento de la búsqueda. Así la procedencia queda registrada
 * aunque la llamada al modelo falle después. No filtra ni modifica nada.
 */
class TrazaVectorStore implements VectorStore {

    private final VectorStore delegado;
    private final ExperimentTraceService traceService;

    TrazaVectorStore(VectorStore delegado, ExperimentTraceService traceService) {
        this.delegado = delegado;
        this.traceService = traceService;
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        List<Document> documentos = delegado.similaritySearch(request);
        traceService.recordDocumentos(documentos);
        return documentos;
    }

    @Override
    public String getName() {
        return delegado.getName();
    }

    @Override
    public void add(List<Document> documents) {
        delegado.add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        delegado.delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        delegado.delete(filterExpression);
    }

    @Override
    public <T> Optional<T> getNativeClient() {
        return delegado.getNativeClient();
    }
}
