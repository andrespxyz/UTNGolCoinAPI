package ec.utn.gol.resources;

import ec.utn.gol.models.*;
import ec.utn.gol.security.Autenticado;
import ec.utn.gol.security.SoloAdmin;
import ec.utn.gol.services.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.math.BigDecimal;
import java.util.Map;

@Path("/predicciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PrediccionResource {

    @Inject
    private PrediccionService prediccionService;

    @Inject
    private BilleteraService billeteraService;

    @Context
    private ContainerRequestContext requestContext;

    // Mismo control que en BilleteraResource: @Autenticado no alcanza para
    // impedir que un usuario apueste o lea predicciones con la billetera de otro.
    private Response verificarPropietario(Long usuarioIdDueño) {
        String rol = (String) requestContext.getProperty("rol");
        if ("admin".equals(rol)) return null;

        String usuarioIdToken = (String) requestContext.getProperty("usuarioId");
        if (usuarioIdDueño == null || usuarioIdToken == null || !usuarioIdToken.equals(String.valueOf(usuarioIdDueño))) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("mensaje", "No tenés permiso para acceder a este recurso"))
                    .build();
        }
        return null;
    }

    @POST
    @Autenticado
    public Response crearPrediccion(Map<String, String> body) {
        try {
            Long billeteraId = Long.parseLong(body.get("billeteraId"));

            Billetera billetera = billeteraService.getBilleteraById(billeteraId);
            if (billetera == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Billetera no encontrada").build();
            }
            Response acceso = verificarPropietario(billetera.getUsuarioId());
            if (acceso != null) return acceso;

            Long partidoId = Long.parseLong(body.get("partidoId"));
            String pronostico = body.get("pronostico");
            BigDecimal monto = new BigDecimal(body.get("monto"));
            String fechaHoraPartido = body.get("fechaHoraPartido");
            Prediccion p = prediccionService.crearPrediccion(billeteraId, partidoId, pronostico, monto, fechaHoraPartido);
            return Response.status(Response.Status.CREATED).entity(p).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/liquidar/{partidoId}")
    @SoloAdmin
    public Response liquidar(@PathParam("partidoId") Long partidoId, Map<String, String> body) {
        String resultadoReal = body != null ? body.get("resultadoReal") : null;
        if (resultadoReal == null || resultadoReal.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("resultadoReal es requerido para liquidar predicciones").build();
        }
        prediccionService.liquidarPredicciones(partidoId, resultadoReal);
        return Response.ok("Predicciones liquidadas").build();
    }

    @GET
    @Path("/billetera/{billeteraId}")
    @Autenticado
    public Response getPredicciones(@PathParam("billeteraId") Long billeteraId) {
        Billetera billetera = billeteraService.getBilleteraById(billeteraId);
        if (billetera == null) return Response.status(Response.Status.NOT_FOUND).build();
        Response acceso = verificarPropietario(billetera.getUsuarioId());
        if (acceso != null) return acceso;

        return Response.ok(prediccionService.getPrediccionesByBilletera(billeteraId)).build();
    }
}