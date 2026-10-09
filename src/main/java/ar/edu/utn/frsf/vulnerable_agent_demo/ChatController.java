package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final SupportAgentService supportAgentService;

    public ChatController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String pregunta) {
        return supportAgentService.consultar(pregunta);
    }
}
