package co.edu.usco.labcontrol.security;

import co.edu.usco.labcontrol.domain.Rol;
import co.edu.usco.labcontrol.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long minutos;
    private final long diasRecordar;
    private final Clock clock;

    public JwtService(@Value("${labcontrol.jwt.secret}") String secret,
                      @Value("${labcontrol.jwt.expiracion-minutos}") long minutos,
                      @Value("${labcontrol.jwt.expiracion-recordar-dias}") long diasRecordar, Clock clock) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("labcontrol.jwt.secret debe tener al menos 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.minutos = minutos;
        this.diasRecordar = diasRecordar;
        this.clock = clock;
    }

    public long expiraEnSegundos(boolean recordar) { return recordar ? diasRecordar * 86400 : minutos * 60; }

    public String generar(Usuario u, boolean recordar) {
        Date ahora = Date.from(clock.instant());
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("correo", u.getCorreo())
                .claim("rol", u.getRol().name())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiraEnSegundos(recordar) * 1000))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new AuthUser(Long.valueOf(c.getSubject()), c.get("correo", String.class),
                    Rol.valueOf(c.get("rol", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
