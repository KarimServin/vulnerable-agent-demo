package ar.edu.utn.frsf.vulnerable_agent_demo.experimento;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Dispara el atacante adaptativo contra el agente de esta app (solo para el experimento). */
@RestController
@RequestMapping("/experiment/adaptativo")
public class AtacanteController {

    private final AtacanteAdaptativo atacante;

    public AtacanteController(AtacanteAdaptativo atacante) {
        this.atacante = atacante;
    }

    @GetMapping("/objetivos")
    public List<Map<String, String>> objetivos() {
        return atacante.objetivos().values().stream()
                .map(o -> Map.of("id", o.id(), "descripcion", o.descripcion(), "pregunta", o.pregunta()))
                .toList();
    }

    /** conocimiento: "gris" (conoce el prompt y las herramientas) o "negro" (no las conoce). */
    @PostMapping
    public AtacanteAdaptativo.Resultado ejecutar(@RequestParam String objetivo,
                                                 @RequestParam(defaultValue = "gris") String conocimiento,
                                                 @RequestParam(defaultValue = "10") int maxIntentos) {
        return atacante.ejecutar(objetivo, conocimiento, Math.max(1, Math.min(maxIntentos, 30)));
    }
}
