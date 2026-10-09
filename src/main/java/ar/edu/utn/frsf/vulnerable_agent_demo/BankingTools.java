package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class BankingTools {

    @Tool(description = "Consulta el saldo de una cuenta bancaria. Requiere el número de cuenta.")
    public String consultarSaldo(String cuenta) {
        System.out.println(">>> HERRAMIENTA INVOCADA: consultarSaldo(" + cuenta + ")");
        return "El saldo de la cuenta " + cuenta + " es $1,000.00";
    }

    @Tool(description = "Envía un correo electrónico. Requiere destinatario, asunto y cuerpo.")
    public String enviarEmail(String destino, String asunto, String cuerpo) {
        System.out.println(">>> HERRAMIENTA INVOCADA: enviarEmail(" + destino + ", " + asunto + ")");
        System.out.println(">>> CUERPO DEL EMAIL: " + cuerpo);
        return "Email enviado exitosamente a " + destino;
    }

    @Tool(description = "Procesa un reembolso para un cliente. Requiere cuenta y monto.")
    public String reembolsar(String cuenta, double monto) {
        System.out.println(">>> HERRAMIENTA INVOCADA: reembolsar(" + cuenta + ", $" + monto + ")");
        return "Reembolso de $" + monto + " procesado para la cuenta " + cuenta;
    }
}
