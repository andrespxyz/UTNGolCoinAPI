package ec.utn.gol.resources;

import ec.utn.gol.models.*;
import ec.utn.gol.services.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.List;

@Path("/billeteras")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BilleteraResource {

    @Inject
    private BilleteraService billeteraService;

    @POST
    public Response crearBilletera(Billetera billetera) {
        try {
            Billetera b = billeteraService.crearBilletera(billetera.getUsuarioId(), billetera.getNombreUsuario());
            return Response.status(Response.Status.CREATED).entity(b).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/usuario/{usuarioId}")
    public Response getBilleteraByUsuario(@PathParam("usuarioId") Long usuarioId) {
        Billetera b = billeteraService.getBilleteraByUsuarioId(usuarioId);
        if (b == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(b).build();
    }

    @GET
    @Path("/{billeteraId}/transacciones")
    public Response getTransacciones(@PathParam("billeteraId") Long billeteraId) {
        List<Transaccion> transacciones = billeteraService.getTransacciones(billeteraId);
        return Response.ok(transacciones).build();
    }

    @POST
    @Path("/{billeteraId}/bono-diario")
    public Response aplicarBonoDiario(@PathParam("billeteraId") Long billeteraId) {
        boolean aplicado = billeteraService.aplicarBonoDiario(billeteraId);
        if (!aplicado) return Response.status(Response.Status.BAD_REQUEST)
                .entity("Bono no aplicable: saldo mayor a cero o ya recibido hoy").build();
        return Response.ok("Bono diario aplicado").build();
    }

    @GET
    @Path("/ranking")
    public Response getRanking() {
        return Response.ok(billeteraService.getRanking()).build();
    }
}