package ar.edu.utn.frsf.vulnerable_agent_demo;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Indica si la base de conocimiento terminó de cargarse. La carga corre después de que la app
 * dice "Started"; medir antes de ese punto da resultados inválidos (almacén vacío o parcial).
 */
@Component
public class BaseDeConocimientoEstado {

    private final AtomicBoolean lista = new AtomicBoolean(false);
    private volatile String corpusHash = "";
    private volatile String corpusModo = "completo";

    public boolean estaLista() {
        return lista.get();
    }

    /** Huella del contenido del corpus: permite saber si dos corridas usaron la misma base. */
    public String corpusHash() {
        return corpusHash;
    }

    public String corpusModo() {
        return corpusModo;
    }

    void marcarLista(String hash, String modo) {
        this.corpusHash = hash;
        this.corpusModo = modo;
        lista.set(true);
    }
}
