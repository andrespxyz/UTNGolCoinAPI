package ec.utn.gol.resources;

import ec.utn.gol.models.*;
import ec.utn.gol.services.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.math.BigDecimal;
import java.util.Map;

@Path("/predicciones")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PrediccionResource {

    @Inject
    private PrediccionService prediccionService;

    @POST
    public Response crearPrediccion(Map<String, String> body) {
        try {
            Long billeteraId = Long.parseLong(body.get("billeteraId"));
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
    public Response liquidar(@PathParam("partidoId") Long partidoId, Map<String, String> body) {
        prediccionService.liquidarPredicciones(partidoId, body.get("resultadoReal"));
        return Response.ok("Predicciones liquidadas").build();
    }

    @GET
    @Path("/billetera/{billeteraId}")
    public Response getPredicciones(@PathParam("billeteraId") Long billeteraId) {
        return Response.ok(prediccionService.getPrediccionesByBilletera(billeteraId)).build();
    }
}