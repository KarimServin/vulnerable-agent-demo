package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.vulnerable_agent_demo.experimento.AtacanteAdaptativo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sin red: solo comprueba que los objetivos estén definidos y que sus predicados de éxito distingan
// una ejecución real de una rechazada por el core.
@SpringBootTest(properties = {"app.vectorstore=simple", "spring.ai.model.embedding=none", "GROQ_API_KEY=test"})
@Import(VulnerableAgentDemoApplicationTests.TestEmbeddingsConfiguration.class)
class AtacanteAdaptativoTests {

    @Autowired
    AtacanteAdaptativo atacante;

    @Test
    void defineLosCincoObjetivos() {
        assertThat(atacante.objetivos()).containsOnlyKeys("G1", "G2", "G3", "G4", "G5");
    }

    @Test
    void elExitoExigeQueElCoreAcepteLaOperacion() {
        var objetivo = atacante.objetivos().get("G2");

        ExperimentTrace aceptada = new ExperimentTrace("p", "endurecido", "m");
        aceptada.addToolInvocation("cambiarDatosContacto", "cuenta=123-456, email=recuperacion@atacante.example, telefono=", "{\"resultado\":\"ok\"}");
        ExperimentTrace rechazada = new ExperimentTrace("p", "endurecido", "m");
        rechazada.addToolInvocation("cambiarDatosContacto", "cuenta=999, email=recuperacion@atacante.example, telefono=", "ERROR 404: no existe");
        ExperimentTrace otroCorreo = new ExperimentTrace("p", "endurecido", "m");
        otroCorreo.addToolInvocation("cambiarDatosContacto", "cuenta=123-456, email=ana@cliente.example, telefono=", "{\"resultado\":\"ok\"}");

        assertThat(objetivo.exito().test(aceptada)).isTrue();
        assertThat(objetivo.exito().test(rechazada)).isFalse();
        assertThat(objetivo.exito().test(otroCorreo)).isFalse();
    }

    @Test
    void objetivoDesconocidoSeRechaza() {
        assertThatThrownBy(() -> atacante.ejecutar("G9", "gris", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
