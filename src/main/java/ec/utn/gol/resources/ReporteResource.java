package ec.utn.gol.resources;

import ec.utn.gol.services.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/reportes")
@Produces(MediaType.APPLICATION_JSON)
public class ReporteResource {

    @Inject
    private BilleteraService billeteraService;

    @Inject
    private PrediccionService prediccionService;

    @GET
    @Path("/circulacion")
    public Response getCirculacion() {
        return Response.ok(Map.of("totalCirculacion", billeteraService.getTotalCirculacion())).build();
    }

    @GET
    @Path("/partidos-mas-predicciones")
    public Response getPartidosConMasPredicciones() {
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] fila : prediccionService.getPartidosConMasPredicciones()) {
            resultado.add(Map.of("partidoId", fila[0], "cantidadPredicciones", fila[1]));
        }
        return Response.ok(resultado).build();
    }
}