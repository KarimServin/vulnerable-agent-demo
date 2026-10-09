package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class VulnerableAgentDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(VulnerableAgentDemoApplication.class, args);
	}

	@Bean
	CommandLineRunner probar(ChatClient.Builder builder) {
		return args -> {
			System.out.println("=== TEST DE CONEXIÓN CON GROQ ===");
			String respuesta = builder.build().prompt()
					.user("Decime en una frase qué es prompt injection")
					.call()
					.content();
			System.out.println("RESPUESTA: " + respuesta);
			System.out.println("=== FIN DEL TEST ===");
		};
	}
}