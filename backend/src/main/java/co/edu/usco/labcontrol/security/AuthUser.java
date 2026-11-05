package co.edu.usco.labcontrol.security;

import co.edu.usco.labcontrol.domain.Rol;

/** Identidad autenticada extraída del JWT. */
public record AuthUser(Long id, String correo, Rol rol) {}
