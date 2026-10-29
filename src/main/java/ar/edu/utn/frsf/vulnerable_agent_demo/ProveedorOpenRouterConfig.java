package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Con OpenRouter, fija el proveedor que atiende el modelo (por ejemplo Groq) para que las corridas usen el mismo servidor.
 * No se puede declarar en propiedades: el binder convierte una lista dentro de un mapa en un objeto y OpenRouter la rechaza.
 * Se activa con {@code app.openrouter.proveedor=Groq}.
 */
@Configuration
@ConditionalOnProperty("app.openrouter.proveedor")
class ProveedorOpenRouterConfig {

    @Bean
    ChatClientCustomizer proveedorOpenRouter(@org.springframework.beans.factory.annotation.Value("${app.openrouter.proveedor}") String proveedor) {
        return builder -> builder.defaultOptions(OpenAiChatOptions.builder()
                .extraBody(Map.of("provider", Map.of("only", List.of(proveedor)))));
    }
}
