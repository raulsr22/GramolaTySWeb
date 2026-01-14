package edu.uclm.es.gramola.model;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * ENTIDAD PARA LA GESTIÓN DE SEGURIDAD MEDIANTE TOKENS.
 * * Esta clase representa un identificador único temporal (Token) utilizado para 
 * procesos críticos que requieren verificación externa:
 * 1. Confirmación de cuenta tras el registro.
 * 2. Validación de solicitudes de recuperación de contraseña.
 * * Se persiste en la base de datos para permitir que los enlaces enviados por 
 * correo electrónico sean válidos incluso si el servidor se reinicia.
 */
@Entity
public class Token {
    
     /**
     * Identificador único del token.
     * Se utiliza un UUID de 36 caracteres.
     * Al no ser secuencial ni predecible, garantiza que un atacante no pueda 
     * adivinar enlaces de confirmación de otros usuarios.
     */
    @Id @Column(length = 36) 
    private String id;

     /**
     * Marca de tiempo de cuándo fue generado el token.
     * Es esencial para implementar la lógica de caducidad (ej: el enlace solo vale 30 min).
     */
    private long creationTime;

     /**
     * Marca de tiempo de cuándo fue consumido el token.
     * Por defecto es 0, lo que indica que el token está "pendiente" o "activo".
     */
    private long useTime = 0;

     /**
     * Constructor por defecto.
     * * Genera automáticamente un nuevo identificador único y registra el 
     * momento exacto de su creación.
     */
    public Token() {
        this.id = UUID.randomUUID().toString();  //Cadena de 36 caracteres
        this.creationTime = System.currentTimeMillis();
    }

     /**
     * Comprueba si el token ya ha sido utilizado.
     * * @return true si el token ya se canjeó (useTime > 0), false en caso contrario.
     */
    public boolean isUsed() {
        return this.useTime > 0;
    }

     /**
     * Marca el token como consumido en el sistema.
     * * Registra el momento exacto del uso, lo que inhabilita el token para 
     * futuros intentos (evitando ataques de reutilización).
     */
    public void use() {
        this.useTime = System.currentTimeMillis();
    }

    // --- GETTERS Y SETTERS ---
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getUseTime() {
        return useTime;
    }

    public void setUseTime(long useTime) {
        this.useTime = useTime;
    }

    
}
