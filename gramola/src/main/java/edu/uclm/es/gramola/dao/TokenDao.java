package edu.uclm.es.gramola.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import edu.uclm.es.gramola.model.Token;

/**
 * REPOSITORIO PARA LA GESTIÓN DE SEGURIDAD (TOKENS).
 * * Esta interfaz gestiona la persistencia de los objetos 'Token' en la base de datos.
 * Los tokens son fundamentales para dos procesos críticos de seguridad en La Gramola:
 * 1. Confirmación de registro (verificación de email).
 * 2. Restablecimiento de contraseña olvidada.
 */
@Repository
public interface TokenDao extends JpaRepository<Token, String> {
    /**
     * Al extender de JpaRepository<Token, String>, esta clase hereda automáticamente
     * toda la lógica necesaria para interactuar con la tabla 'tokens':
     * - save(Token): Permite persistir un nuevo token generado por el UserService.
     * - findById(String id): Permite recuperar un token a partir del UUID enviado 
     * por el usuario en el enlace del correo electrónico.
     * - deleteById(String id): Permite limpiar tokens antiguos o usados de la base de datos.
     */
}