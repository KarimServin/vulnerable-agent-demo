package ar.edu.utn.frsf.vulnerable_agent_demo.core;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Webservice simulado del core bancario. Sin autenticación a propósito: el
 * agente es el único cliente y su exceso de autoridad es parte del caso.
 */
@RestController
public class CoreBancarioController {

    public record ComprobanteRequest(String cuenta, String transaccion, String destino) {
    }

    public record CorreoRequest(String destino, String asunto, String cuerpo) {
    }

    public record ReembolsoRequest(String cuenta, double monto, String motivo) {
    }

    public record TransferenciaRequest(String origen, String destino, double monto) {
    }

    public record ContactoRequest(String email, String telefono) {
    }

    private final CoreBancario core;

    public CoreBancarioController(CoreBancario core) {
        this.core = core;
    }

    // --- Lectura (sensibilidad baja) ---

    @GetMapping("/api/cuentas/{cuenta}/saldo")
    public Map<String, Object> saldo(@PathVariable String cuenta) {
        return Map.of("cuenta", cuenta, "saldo", core.saldo(cuenta));
    }

    @GetMapping("/api/cuentas/{cuenta}/movimientos")
    public List<CoreBancario.Movimiento> movimientos(@PathVariable String cuenta) {
        return core.movimientos(cuenta);
    }

    // --- Comunicación (sensibilidad media) ---

    @PostMapping("/api/comprobantes")
    public Map<String, String> comprobante(@RequestBody ComprobanteRequest r) {
        return Map.of("resultado", core.enviarComprobante(r.cuenta(), r.transaccion(), r.destino()));
    }

    @PostMapping("/api/correos")
    public Map<String, String> correo(@RequestBody CorreoRequest r) {
        return Map.of("resultado", core.enviarCorreo(r.destino(), r.asunto(), r.cuerpo()));
    }

    @PostMapping("/api/tarjetas/{tarjeta}/bloqueo")
    public Map<String, String> bloquear(@PathVariable String tarjeta) {
        return Map.of("resultado", core.bloquearTarjeta(tarjeta));
    }

    // --- Movimiento de dinero y datos de identidad (sensibilidad alta) ---

    @PostMapping("/api/reembolsos")
    public Map<String, String> reembolso(@RequestBody ReembolsoRequest r) {
        return Map.of("resultado", core.reembolsar(r.cuenta(), r.monto(), r.motivo()));
    }

    @PostMapping("/api/transferencias")
    public Map<String, String> transferencia(@RequestBody TransferenciaRequest r) {
        return Map.of("resultado", core.transferir(r.origen(), r.destino(), r.monto()));
    }

    @PutMapping("/api/clientes/{cuenta}/contacto")
    public Map<String, String> contacto(@PathVariable String cuenta, @RequestBody ContactoRequest r) {
        return Map.of("resultado", core.cambiarContacto(cuenta, r.email(), r.telefono()));
    }

    // --- Soporte del experimento ---

    @GetMapping("/mock/estado")
    public CoreBancario.Estado estado() {
        return core.estado();
    }

    @PostMapping("/mock/reset")
    public Map<String, String> reset() {
        core.reset();
        return Map.of("resultado", "estado restablecido");
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> noEncontrado(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
