package ec.utn.gol.resources;

import ec.utn.gol.models.*;
import ec.utn.gol.security.Autenticado;
import ec.utn.gol.services.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import java.util.List;
import java.util.Map;

@Path("/billeteras")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BilleteraResource {

    @Inject
    private BilleteraService billeteraService;

    @Context
    private ContainerRequestContext requestContext;

    // RF25 — @Autenticado solo exige un JWT válido, no que el recurso pedido
    // sea del dueño del token: sin esto, cualquier usuario autenticado podía
    // leer/mover el saldo de cualquier otro usuario cambiando el id en la URL.
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
    public Response crearBilletera(Billetera billetera) {
        if (billetera == null || billetera.getUsuarioId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("mensaje", "Falta usuarioId en el cuerpo de la petición")).build();
        }
        Response acceso = verificarPropietario(billetera.getUsuarioId());
        if (acceso != null) return acceso;

        try {
            Billetera b = billeteraService.crearBilletera(billetera.getUsuarioId(), billetera.getNombreUsuario());
            return Response.status(Response.Status.CREATED).entity(b).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/usuario/{usuarioId}")
    @Autenticado
    public Response getBilleteraByUsuario(@PathParam("usuarioId") Long usuarioId) {
        Response acceso = verificarPropietario(usuarioId);
        if (acceso != null) return acceso;

        Billetera b = billeteraService.getBilleteraByUsuarioId(usuarioId);
        if (b == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(b).build();
    }

    @GET
    @Path("/{billeteraId}/transacciones")
    @Autenticado
    public Response getTransacciones(@PathParam("billeteraId") Long billeteraId) {
        Billetera b = billeteraService.getBilleteraById(billeteraId);
        if (b == null) return Response.status(Response.Status.NOT_FOUND).build();
        Response acceso = verificarPropietario(b.getUsuarioId());
        if (acceso != null) return acceso;

        List<Transaccion> transacciones = billeteraService.getTransacciones(billeteraId);
        return Response.ok(transacciones).build();
    }

    @POST
    @Path("/{billeteraId}/bono-diario")
    @Autenticado
    public Response aplicarBonoDiario(@PathParam("billeteraId") Long billeteraId) {
        Billetera b = billeteraService.getBilleteraById(billeteraId);
        if (b == null) return Response.status(Response.Status.NOT_FOUND).build();
        Response acceso = verificarPropietario(b.getUsuarioId());
        if (acceso != null) return acceso;

        boolean aplicado = billeteraService.aplicarBonoDiario(billeteraId);
        if (!aplicado) return Response.status(Response.Status.BAD_REQUEST)
                .entity("Bono no aplicable: saldo mayor a cero o ya recibido hoy").build();
        return Response.ok("Bono diario aplicado").build();
    }

    // RF21 — ranking público, sin autenticación.
    @GET
    @Path("/ranking")
    public Response getRanking() {
        return Response.ok(billeteraService.getRanking()).build();
    }
}