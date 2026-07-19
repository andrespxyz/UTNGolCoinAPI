package ec.utn.gol.resources;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @GET
    @Path("/ping")
    public Response ping() {
        return Response.ok("{\"status\":\"UTNGolCoinAPI activo\"}").build();
    }
}