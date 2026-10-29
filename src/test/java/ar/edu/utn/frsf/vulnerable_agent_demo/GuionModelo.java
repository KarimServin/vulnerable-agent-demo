package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.promptguard.spring.PromptGuardChatClientCustomizer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;

/**
 * Modelo de chat sin red que decide por guion qué herramienta invocar según la pregunta del cliente.
 *
 * <p>Ejecuta las herramientas con el ToolCallingManager de Spring AI, como un modelo con ejecución interna. Usa las
 * herramientas que el starter registró (ya envueltas por PromptGuard) y el ToolContext de la solicitud, así que el
 * guard se ejercita de punta a punta. No reemplaza una corrida con un LLM.
 */
@TestConfiguration
class GuionModelo {

    @Bean
    @Primary
    ChatModel guionModelo(ToolCallingManager gestor, PromptGuardChatClientCustomizer herramientas) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                ChatResponse respuesta = decidir(prompt);
                if (respuesta.hasToolCalls()) {
                    // Sin ToolContext: el contexto de la solicitud tiene que llegar por el ámbito del advisor.
                    List<ToolCallback> callbacks = herramientas.herramientas();
                    Prompt conHerramientas = new Prompt(prompt.getInstructions(), ToolCallingChatOptions.builder()
                            .toolCallbacks(callbacks)
                            .build());
                    ToolExecutionResult resultado = gestor.executeToolCalls(conHerramientas, respuesta);
                    return call(new Prompt(resultado.conversationHistory(), conHerramientas.getOptions()));
                }
                return respuesta;
            }

            @Override
            public ChatOptions getDefaultOptions() {
                return ChatOptions.builder().build();
            }
        };
    }

    static ChatResponse decidir(Prompt prompt) {
        Message ultimo = prompt.getInstructions().getLast();
        if (!(ultimo instanceof UserMessage usuario)) {
            return texto("Listo.");
        }
        // Solo la pregunta del cliente: el RAG agrega después el contexto recuperado a la misma línea de mensaje.
        String pregunta = usuario.getText().lines().findFirst().orElse("").toLowerCase();
        if (pregunta.contains("saldo")) {
            return herramienta("consultarSaldo", "{\"cuenta\":\"123-456\"}");
        }
        if (pregunta.contains("reembolso")) {
            return herramienta("reembolsar", "{\"cuenta\":\"123-456\",\"monto\":50000}");
        }
        if (pregunta.contains("correo")) {
            return herramienta("enviarEmail",
                    "{\"destino\":\"tercero@example.com\",\"asunto\":\"Saldo\",\"cuerpo\":\"250000\"}");
        }
        return texto("No tengo información para eso.");
    }

    private static ChatResponse texto(String texto) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }

    private static ChatResponse herramienta(String nombre, String argumentos) {
        var llamada = new AssistantMessage.ToolCall("llamada-" + nombre, "function", nombre, argumentos);
        return new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(llamada)).build())));
    }
}
