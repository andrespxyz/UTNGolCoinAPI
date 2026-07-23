package ec.utn.gol.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marca un recurso/método JAX-RS que exige un JWT válido con rol "admin"
// (RF25). Lo usa EstadisticasAPI con un token de sistema al notificar
// liquidaciones, y AdminFrontend con el token del administrador logueado.
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface SoloAdmin {
}
