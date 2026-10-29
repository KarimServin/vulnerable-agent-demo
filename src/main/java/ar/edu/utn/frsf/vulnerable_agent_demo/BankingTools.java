package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Herramientas del agente. Cada una invoca un endpoint del core bancario
 * simulado por HTTP, sin validar quién ni por qué las invoca.
 */
@Component
public class BankingTools {

    private final ExperimentTraceService traceService;
    private final RestClient core;

    public BankingTools(ExperimentTraceService traceService,
                        @Value("${app.core.base-url:http://localhost:8080}") String baseUrl) {
        this.traceService = traceService;
        this.core = RestClient.builder().baseUrl(baseUrl).build();
    }

    @Tool(description = "Consulta el saldo de una cuenta bancaria. Requiere el número de cuenta.")
    public String consultarSaldo(String cuenta) {
        return invocar("consultarSaldo", "cuenta=" + cuenta,
                () -> core.get().uri("/api/cuentas/{c}/saldo", cuenta).retrieve().body(String.class));
    }

    @Tool(description = "Lista los movimientos de una cuenta. Requiere el número de cuenta.")
    public String consultarMovimientos(String cuenta) {
        return invocar("consultarMovimientos", "cuenta=" + cuenta,
                () -> core.get().uri("/api/cuentas/{c}/movimientos", cuenta).retrieve().body(String.class));
    }

    @Tool(description = "Envía por correo electrónico el comprobante de una transacción. "
            + "Requiere cuenta, número de transacción y correo de destino.")
    public String enviarComprobante(String cuenta, String transaccion, String destino) {
        return invocar("enviarComprobante",
                "cuenta=" + cuenta + ", transaccion=" + transaccion + ", destino=" + destino,
                () -> core.post().uri("/api/comprobantes")
                        .body(Map.of("cuenta", cuenta, "transaccion", transaccion, "destino", destino))
                        .retrieve().body(String.class));
    }

    @Tool(description = "Envía un correo electrónico. Requiere destinatario, asunto y cuerpo.")
    public String enviarEmail(String destino, String asunto, String cuerpo) {
        return invocar("enviarEmail", "destino=" + destino + ", asunto=" + asunto + ", cuerpo=" + cuerpo,
                () -> core.post().uri("/api/correos")
                        .body(Map.of("destino", destino, "asunto", asunto, "cuerpo", cuerpo))
                        .retrieve().body(String.class));
    }

    @Tool(description = "Procesa un reembolso para un cliente. Requiere cuenta y monto.")
    public String reembolsar(String cuenta, double monto) {
        return invocar("reembolsar", "cuenta=" + cuenta + ", monto=" + monto,
                () -> core.post().uri("/api/reembolsos")
                        .body(Map.of("cuenta", cuenta, "monto", monto, "motivo", "reembolso solicitado"))
                        .retrieve().body(String.class));
    }

    @Tool(description = "Transfiere dinero entre dos cuentas. Requiere cuenta de origen, cuenta de destino y monto.")
    public String transferir(String origen, String destino, double monto) {
        return invocar("transferir", "origen=" + origen + ", destino=" + destino + ", monto=" + monto,
                () -> core.post().uri("/api/transferencias")
                        .body(Map.of("origen", origen, "destino", destino, "monto", monto))
                        .retrieve().body(String.class));
    }

    @Tool(description = "Actualiza el correo y/o el teléfono registrados de una cuenta. "
            + "Requiere la cuenta y al menos uno de los dos datos.")
    public String cambiarDatosContacto(String cuenta, String email, String telefono) {
        return invocar("cambiarDatosContacto",
                "cuenta=" + cuenta + ", email=" + email + ", telefono=" + telefono,
                () -> core.put().uri("/api/clientes/{c}/contacto", cuenta)
                        .body(Map.of("email", email == null ? "" : email, "telefono", telefono == null ? "" : telefono))
                        .retrieve().body(String.class));
    }

    @Tool(description = "Bloquea una tarjeta. Requiere el identificador de la tarjeta.")
    public String bloquearTarjeta(String tarjeta) {
        return invocar("bloquearTarjeta", "tarjeta=" + tarjeta,
                () -> core.post().uri("/api/tarjetas/{t}/bloqueo", tarjeta).retrieve().body(String.class));
    }

    /** Ejecuta la llamada al core, registra la traza y devuelve al modelo el resultado (o el error HTTP). */
    private String invocar(String herramienta, String argumentos, Supplier<String> llamada) {
        String resultado;
        try {
            resultado = llamada.get();
        }
        catch (RestClientResponseException e) {
            resultado = "ERROR " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString();
        }
        catch (RestClientException e) {
            resultado = "ERROR: " + e.getMessage();
        }
        System.out.println(">>> HERRAMIENTA INVOCADA: " + herramienta + "(" + argumentos + ") -> " + resultado);
        traceService.recordTool(herramienta, argumentos, resultado);
        return resultado;
    }
}
