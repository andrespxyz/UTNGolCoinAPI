package ec.utn.gol.filters;

import ec.utn.gol.security.Autenticado;
import ec.utn.gol.security.JwtUtil;
import ec.utn.gol.security.SoloAdmin;
import io.jsonwebtoken.Claims;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.Method;
import java.util.Map;

// RF25 — protege las rutas administrativas/privadas de UTNGolCoinAPI.
// No es @PreMatching: corre después de resolver el recurso, por eso puede
// leer las anotaciones @Autenticado/@SoloAdmin del método o la clase.
@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        Method metodo = resourceInfo.getResourceMethod();
        Class<?> recurso = resourceInfo.getResourceClass();

        boolean requiereAdmin = metodo.isAnnotationPresent(SoloAdmin.class)
                || recurso.isAnnotationPresent(SoloAdmin.class);
        boolean requiereAuth = requiereAdmin
                || metodo.isAnnotationPresent(Autenticado.class)
                || recurso.isAnnotationPresent(Autenticado.class);

        if (!requiereAuth) return; // ruta pública (ranking, ping, etc.)

        String header = requestContext.getHeaderString("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            abortar(requestContext, "Falta el token de autenticación (Authorization: Bearer <token>)");
            return;
        }

        try {
            Claims claims = JwtUtil.validar(header.substring(7));
            String rol = claims.get("rol", String.class);

            if (requiereAdmin && !"admin".equals(rol)) {
                abortar(requestContext, "Esta operación requiere rol administrador");
                return;
            }

            requestContext.setProperty("usuarioId", claims.getSubject());
            requestContext.setProperty("rol", rol);
        } catch (Exception e) {
            abortar(requestContext, "Token inválido o expirado");
        }
    }

    private void abortar(ContainerRequestContext ctx, String mensaje) {
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("mensaje", mensaje))
                .type(MediaType.APPLICATION_JSON)
                .build());
    }
}
