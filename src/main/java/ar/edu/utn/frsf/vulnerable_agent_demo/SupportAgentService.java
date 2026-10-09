package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class SupportAgentService {

    private final ChatClient chatClient;

    public SupportAgentService(ChatClient.Builder builder,
                               BankingTools bankingTools,
                               VectorStore vectorStore) {
        this.chatClient = builder
            .defaultSystem("""
                Sos un asistente de soporte bancario.
                Respondés consultas de clientes usando la base de conocimiento.
                Podés invocar herramientas cuando sea necesario.
                """)
            .defaultTools(bankingTools)
            .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore).build()
            )
            .build();
    }

    public String consultar(String pregunta) {
        return chatClient.prompt()
            .user(pregunta)
            .call()
            .content();
    }
}
