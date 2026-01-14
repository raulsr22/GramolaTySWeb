package edu.uclm.es.gramola.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * ENTIDAD PARA LA GESTIÓN DE PROPIETARIOS DE BARES (ESTABLECIMIENTOS).
 * * Esta clase es el centro del sistema de seguridad e identidad. Almacena las 
 * credenciales de acceso, la configuración de integración con Spotify y los 
 * datos necesarios para los requisitos adicionales (GPS y Firma).
*/
@Entity
@Table(name = "users")
public class User {
    /**
     * Identificador único de la cuenta. 
     * Se utiliza el correo electrónico como clave primaria.
    */
    @Id
    private String email;
    
    /**
     * Nombre del establecimiento.
    */
    @Column(length = 100)
    private String bar;

    /**
     * Clave secreta (Client Secret) de Spotify.
     * Es esencial para el flujo OAuth 2.0 y nunca debe ser expuesta al Frontend.
    */
    private String clientSecret; 
    // --------------------------------

    /**
     * Contraseña de acceso al panel de gestión, almacenada siempre en formato HASH (SHA-256).
    */
    private String pwd;

    /**
     * Identificador público (Client ID) de la aplicación en el Dashboard de Spotify.
    */
    private String clientId;

    // --- REQUISITO EXTRA A: GEOLOCALIZACIÓN ---
    
    /**
     * Dirección postal introducida por el usuario durante el registro.
    */
    private String address;
    
    /**
     * Coordenada de latitud obtenida mediante el GeocodingService.
    */
    private Double lat;

    /**
     * Coordenada de longitud obtenida mediante el GeocodingService.
    */
    private Double lng;

    /**
     * Token de acceso actual de Spotify. 
     * Se persiste para que el bar pueda mantener la sesión activa entre reinicios del servidor.
    */
    @Column(length = 1000)
    private String spotifyAccessToken;

    // --- REQUISITO EXTRA C: FIRMA DIGITAL ---
    
    /**
     * Almacena la representación gráfica de la firma del propietario.
     * Se guarda como una cadena de texto en formato Base64.
     * * Se usa @Lob y LONGTEXT para permitir el almacenamiento de imágenes de alta resolución.
    */
    @Lob 
    @Column(columnDefinition = "LONGTEXT") 
    private String signature;

    /**
     * Vinculación con el token de seguridad para la confirmación de cuenta o reset de contraseña.
     * Se configura con 'CascadeType.ALL' para que al borrar un usuario se elimine su token asociado.
    */
    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true, optional = false)
    @JoinColumn(name = "creation_token_id", referencedColumnName = "id")
    private Token creationToken;

    // ---------------------------------------------------------
    // GETTERS Y SETTERS (Acceso y Modificación de Atributos)
    // ---------------------------------------------------------
    
    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Setter especial para la contraseña.
     * SEGURIDAD: Antes de asignar el valor al campo, se procede a su encriptación 
     * inmediata para asegurar que nunca se guarde texto plano en la base de datos.
     */
    public void setPwd(String pwd) {
        this.pwd = this.encryptPassword(pwd);  
    }

    public String getPwd() {
        return pwd;  
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCreationToken(Token token) {
        this.creationToken = token;
    }

    public Token getCreationToken() {
        return creationToken;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    // 🚨 Getters y Setters para Geolocalización
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    // 🚨 Getters y Setters para Firma Digital
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getSpotifyAccessToken() { return spotifyAccessToken; }
    public void setSpotifyAccessToken(String spotifyAccessToken) { this.spotifyAccessToken = spotifyAccessToken; }
    
    /**
     * Aplica el algoritmo SHA-256 a una cadena de texto.
     * Se utiliza para proteger la privacidad de las contraseñas de los bares.
     * * @param password Texto original introducido por el usuario.
     * @return Cadena de 64 caracteres en formato hexadecimal representativa del hash.
    */
    public String encryptPassword(String password) {
        try {
            MessageDigest digest;
            digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            
            // Conversión de bytes a representación hexadecimal legible
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Error crítico si el entorno Java no soporta SHA-256
            throw new RuntimeException("Error al encriptar la contraseña", e);
        }
    }

}