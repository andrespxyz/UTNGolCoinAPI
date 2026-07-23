package ec.utn.gol.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;

public class JwtUtil {

    // Debe coincidir EXACTAMENTE con Jwt:SecretKey en appsettings.json de
    // EstadisticasAPI — es la clave compartida HS256 entre ambos backends.
    // En un despliegue real esto debería venir de una variable de entorno,
    // no de código fuente; se deja así por ser un proyecto académico.
    private static final String SECRET_KEY =
            "UTNGolMundial2026-ClaveSecretaCompartida-EstadisticasAPI-UTNGolCoinAPI-CambiarEnProduccion";

    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));

    // Lanza JwtException (incluye ExpiredJwtException, SignatureException, etc.)
    // si el token es inválido, expiró o fue alterado.
    public static Claims validar(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
