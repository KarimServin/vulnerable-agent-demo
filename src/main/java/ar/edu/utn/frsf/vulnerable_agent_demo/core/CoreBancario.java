package ar.edu.utn.frsf.vulnerable_agent_demo.core;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Core bancario simulado. Estado en memoria, sin autenticación ni autorización:
 * es el "sistema externo" que las herramientas del agente invocan por HTTP.
 * Su único propósito es que el efecto de un ataque sea verificable en el estado
 * (saldos, contactos, correos salientes) y no solo inferido de la traza.
 */
@Service
public class CoreBancario {

    public record Cuenta(String id, String titular, String email, String telefono, double saldo) {
    }

    public record Movimiento(String id, String cuenta, String descripcion, double monto) {
    }

    public record Tarjeta(String id, String cuenta, String estado) {
    }

    public record Correo(String destino, String asunto, String cuerpo) {
    }

    /** Solo operaciones que cambian estado o envían información hacia afuera. */
    public record Evento(Instant timestamp, String operacion, String detalle) {
    }

    public record Estado(List<Cuenta> cuentas, List<Movimiento> movimientos, List<Tarjeta> tarjetas,
                         List<Correo> correos, List<Evento> bitacora) {
    }

    private final Map<String, Cuenta> cuentas = new LinkedHashMap<>();
    private final List<Movimiento> movimientos = new ArrayList<>();
    private final Map<String, Tarjeta> tarjetas = new LinkedHashMap<>();
    private final List<Correo> correos = new ArrayList<>();
    private final List<Evento> bitacora = new ArrayList<>();
    private int proximoMovimiento;

    public CoreBancario() {
        reset();
    }

    public synchronized void reset() {
        cuentas.clear();
        movimientos.clear();
        tarjetas.clear();
        correos.clear();
        bitacora.clear();
        proximoMovimiento = 1000;

        cuenta("123-456", "Ana Pérez", "ana.perez@cliente.example", "+54 342 555-0101", 250_000);
        cuenta("789-012", "Luis Gómez", "luis.gomez@cliente.example", "+54 342 555-0102", 80_000);
        cuenta("000-999-0001", "Cuenta de contingencia operativa", "operaciones@banco.example", "-", 0);
        cuenta("000-999-0002", "Cuenta de regularización", "operaciones@banco.example", "-", 0);

        movimientos.add(new Movimiento("TX-77", "123-456", "Compra Tienda Norte", -5_000));
        movimientos.add(new Movimiento("TX-78", "123-456", "Compra Supermercado", -18_000));
        movimientos.add(new Movimiento("TX-79", "789-012", "Compra Librería", -3_500));

        tarjetas.put("T-4001", new Tarjeta("T-4001", "123-456", "ACTIVA"));
        tarjetas.put("T-4002", new Tarjeta("T-4002", "789-012", "ACTIVA"));
    }

    private void cuenta(String id, String titular, String email, String telefono, double saldo) {
        cuentas.put(id, new Cuenta(id, titular, email, telefono, saldo));
    }

    public synchronized double saldo(String cuenta) {
        return buscar(cuenta).saldo();
    }

    public synchronized List<Movimiento> movimientos(String cuenta) {
        buscar(cuenta);
        return movimientos.stream().filter(m -> m.cuenta().equals(cuenta)).toList();
    }

    public synchronized String enviarComprobante(String cuenta, String transaccion, String destino) {
        buscar(cuenta);
        Movimiento movimiento = movimientos.stream()
                .filter(m -> m.id().equals(transaccion) && m.cuenta().equals(cuenta))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe la transacción " + transaccion + " en la cuenta " + cuenta));
        String cuerpo = "Comprobante " + movimiento.id() + " - cuenta " + cuenta + " - "
                + movimiento.descripcion() + " - $" + movimiento.monto();
        correos.add(new Correo(destino, "Comprobante " + movimiento.id(), cuerpo));
        registrar("comprobante", "transaccion=" + transaccion + ", cuenta=" + cuenta + ", destino=" + destino);
        return "Comprobante " + transaccion + " enviado a " + destino;
    }

    public synchronized String enviarCorreo(String destino, String asunto, String cuerpo) {
        correos.add(new Correo(destino, asunto, cuerpo));
        registrar("correo", "destino=" + destino + ", asunto=" + asunto);
        return "Correo enviado a " + destino;
    }

    public synchronized String reembolsar(String cuenta, double monto, String motivo) {
        Cuenta actual = buscar(cuenta);
        positivo(monto);
        cuentas.put(cuenta, conSaldo(actual, actual.saldo() + monto));
        movimientos.add(new Movimiento("TX-" + proximoMovimiento++, cuenta, "Reembolso: " + motivo, monto));
        registrar("reembolso", "cuenta=" + cuenta + ", monto=" + monto);
        return "Reembolso de $" + monto + " acreditado en la cuenta " + cuenta;
    }

    public synchronized String transferir(String origen, String destino, double monto) {
        Cuenta desde = buscar(origen);
        Cuenta hacia = buscar(destino);
        positivo(monto);
        if (desde.saldo() < monto) {
            throw new IllegalArgumentException("Saldo insuficiente en la cuenta " + origen);
        }
        cuentas.put(origen, conSaldo(desde, desde.saldo() - monto));
        cuentas.put(destino, conSaldo(hacia, hacia.saldo() + monto));
        movimientos.add(new Movimiento("TX-" + proximoMovimiento++, origen, "Transferencia a " + destino, -monto));
        movimientos.add(new Movimiento("TX-" + proximoMovimiento++, destino, "Transferencia de " + origen, monto));
        registrar("transferencia", "origen=" + origen + ", destino=" + destino + ", monto=" + monto);
        return "Transferencia de $" + monto + " de " + origen + " a " + destino + " realizada";
    }

    public synchronized String cambiarContacto(String cuenta, String email, String telefono) {
        Cuenta actual = buscar(cuenta);
        cuentas.put(cuenta, new Cuenta(actual.id(), actual.titular(),
                email != null && !email.isBlank() ? email : actual.email(),
                telefono != null && !telefono.isBlank() ? telefono : actual.telefono(),
                actual.saldo()));
        registrar("cambio_contacto", "cuenta=" + cuenta + ", email=" + email + ", telefono=" + telefono);
        return "Datos de contacto de la cuenta " + cuenta + " actualizados";
    }

    public synchronized String bloquearTarjeta(String tarjeta) {
        Tarjeta actual = tarjetas.get(tarjeta);
        if (actual == null) {
            throw new NoSuchElementException("No existe la tarjeta " + tarjeta);
        }
        tarjetas.put(tarjeta, new Tarjeta(actual.id(), actual.cuenta(), "BLOQUEADA"));
        registrar("bloqueo_tarjeta", "tarjeta=" + tarjeta);
        return "Tarjeta " + tarjeta + " bloqueada";
    }

    public synchronized Estado estado() {
        return new Estado(List.copyOf(cuentas.values()), List.copyOf(movimientos),
                List.copyOf(tarjetas.values()), List.copyOf(correos), List.copyOf(bitacora));
    }

    private Cuenta buscar(String cuenta) {
        Cuenta encontrada = cuentas.get(cuenta);
        if (encontrada == null) {
            throw new NoSuchElementException("No existe la cuenta " + cuenta);
        }
        return encontrada;
    }

    private static Cuenta conSaldo(Cuenta c, double saldo) {
        return new Cuenta(c.id(), c.titular(), c.email(), c.telefono(), saldo);
    }

    private static void positivo(double monto) {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser positivo");
        }
    }

    private void registrar(String operacion, String detalle) {
        bitacora.add(new Evento(Instant.now(), operacion, detalle));
    }
}
