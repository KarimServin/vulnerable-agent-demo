package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.vulnerable_agent_demo.core.CoreBancario;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreBancarioTest {

    private final CoreBancario core = new CoreBancario();

    @Test
    void reembolsoAcreditaSaldoYQuedaEnBitacora() {
        double antes = core.saldo("123-456");

        core.reembolsar("123-456", 1_000, "prueba");

        assertThat(core.saldo("123-456")).isEqualTo(antes + 1_000);
        assertThat(core.estado().bitacora()).extracting(CoreBancario.Evento::operacion).containsExactly("reembolso");
    }

    @Test
    void transferenciaMueveDineroEntreCuentas() {
        core.transferir("123-456", "000-999-0002", 20_000);

        assertThat(core.saldo("000-999-0002")).isEqualTo(20_000);
        assertThat(core.saldo("123-456")).isEqualTo(230_000);
    }

    @Test
    void transferenciaSinSaldoSeRechazaYNoCambiaEstado() {
        assertThatThrownBy(() -> core.transferir("789-012", "123-456", 9_999_999))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(core.estado().bitacora()).isEmpty();
    }

    @Test
    void cambioDeContactoModificaElCorreoRegistrado() {
        core.cambiarContacto("123-456", "otro@externo.example", null);

        assertThat(core.estado().cuentas())
                .filteredOn(c -> c.id().equals("123-456"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.email()).isEqualTo("otro@externo.example");
                    assertThat(c.telefono()).isEqualTo("+54 342 555-0101");
                });
    }

    @Test
    void cuentaInexistenteFalla() {
        assertThatThrownBy(() -> core.reembolsar("999-999", 10, "x")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resetRestauraElEstadoInicial() {
        core.reembolsar("123-456", 5_000, "x");
        core.enviarCorreo("a@b.example", "s", "c");

        core.reset();

        assertThat(core.saldo("123-456")).isEqualTo(250_000);
        assertThat(core.estado().correos()).isEmpty();
        assertThat(core.estado().bitacora()).isEmpty();
    }
}
