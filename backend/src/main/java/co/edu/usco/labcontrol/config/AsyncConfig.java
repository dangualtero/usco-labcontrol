package co.edu.usco.labcontrol.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas en segundo plano:
 *  - los avisos de novedades (n8n / correo), para que el auxiliar no espere a la IA ni al correo;
 *  - el cifrado periódico de contraseñas escritas en texto normal directamente en MySQL ({@code CifradoContrasenas}).
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
