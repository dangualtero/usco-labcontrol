package co.edu.usco.labcontrol.security;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** RNF-03: autenticación obligatoria y autorización por roles (Estudiante/Docente, Auxiliar, Coordinador). */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)           // API stateless con Bearer token
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h.frameOptions(f -> f.deny()).contentTypeOptions(Customizer.withDefaults()))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/salud").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/registro/verificar", "/api/auth/registro/reenviar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/sedes", "/api/imagenes/*").permitAll()
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/equipos/**").authenticated()
                .requestMatchers("/api/prestamos/**").hasAnyRole("ESTUDIANTE", "DOCENTE")
                .requestMatchers("/api/ventanilla/**").hasRole("AUXILIAR")
                .requestMatchers("/api/coordinador/**").hasRole("COORDINADOR")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> escribir(res, 401, "Sesión no válida o expirada"))
                .accessDeniedHandler((req, res, ex) -> escribir(res, 403, "Acceso restringido: su rol no permite esta función")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, int status, String msg) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }

    @Bean
    CorsConfigurationSource cors(@Value("${labcontrol.cors.origenes}") String origenes) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Sede"));
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/api/**", c);
        return s;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean Clock clock() { return Clock.systemDefaultZone(); }
}
