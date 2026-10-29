package ar.edu.utn.frsf.vulnerable_agent_demo;

import ar.edu.utn.frsf.promptguard.core.AutorizacionRecursos;
import ar.edu.utn.frsf.promptguard.core.Identidad;
import ar.edu.utn.frsf.promptguard.spring.ProveedorIdentidad;
import ar.edu.utn.frsf.vulnerable_agent_demo.core.CoreBancario;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Autorización por usuario para el demo (perfil {@code promptguard-usuarios}, control C5 de PromptGuard).
 *
 * <p>La app vulnerable no tiene login: la identidad se toma del encabezado {@code X-Usuario} como sustituto de una
 * autenticación real, que en producción vendría de Spring Security o de un token. Lo que sí es real es la separación
 * de responsabilidades: PromptGuard no conoce cuentas ni tarjetas, solo le pregunta a este código si el usuario tiene
 * derecho sobre el recurso.
 */
@Configuration
@Profile("promptguard-usuarios")
public class AutorizacionDemo {

    static final String ENCABEZADO = "X-Usuario";

    /** Usuario autenticado de la demo → cuenta de la que es titular. */
    private static final Map<String, String> CUENTA_DEL_USUARIO = Map.of("ana", "123-456", "luis", "789-012");

    @Bean
    public ProveedorIdentidad proveedorIdentidad() {
        return request -> {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes http)) {
                return Optional.empty();
            }
            String usuario = http.getRequest().getHeader(ENCABEZADO);
            if (usuario == null || !CUENTA_DEL_USUARIO.containsKey(usuario)) {
                return Optional.empty();
            }
            return Optional.of(new Identidad(usuario, Set.of("cliente")));
        };
    }

    @Bean
    public AutorizacionRecursos autorizacionRecursos(CoreBancario core) {
        return (usuario, tipo, valor) -> {
            String cuentaPropia = CUENTA_DEL_USUARIO.get(usuario);
            if (cuentaPropia == null) {
                return false;
            }
            return switch (tipo) {
                case "cuenta" -> cuentaPropia.equals(valor);
                case "tarjeta" -> core.estado().tarjetas().stream()
                        .anyMatch(t -> t.id().equals(valor) && t.cuenta().equals(cuentaPropia));
                default -> false;
            };
        };
    }
}
