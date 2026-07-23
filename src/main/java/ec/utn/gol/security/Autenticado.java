package ec.utn.gol.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marca un recurso/método JAX-RS que exige un JWT válido (cualquier rol) en
// el header Authorization: Bearer <token>. Ver JwtAuthFilter.
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Autenticado {
}
