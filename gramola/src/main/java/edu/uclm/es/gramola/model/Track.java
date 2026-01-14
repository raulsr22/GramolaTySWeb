package edu.uclm.es.gramola.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ENTIDAD PARA EL HISTORIAL DE REPRODUCCIÓN (TABLA TRACKS).
 * * Esta clase representa cada una de las canciones que han sido solicitadas 
 * y pagadas por los clientes. Funciona como un registro 
 * que permite al propietario del bar consultar qué música se ha puesto.
 */
@Entity
@Table(name = "tracks")
public class Track {
    
     /**
     * Identificador interno único para la base de datos.
     * Se utiliza una clave primaria autoincremental independiente del ID de Spotify
     * para permitir que una misma canción (ej: "Pájaros de Barro") pueda aparecer múltiples 
     * veces en el historial si ha sido pagada en distintos momentos.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long internalId; // Clave primaria autoincremental para permitir múltiples entradas de la misma canción

    /**
    * Identificador único de la canción proporcionado por la API de Spotify.
    */
    private String spotifyId; 

    /**
     * Título de la canción en el momento de la solicitud.
    */
    private String title;

    /**
    * Artista o grupo musical de la canción.
    */
    private String artist;

    /**
    * Correo electrónico del establecimiento o cliente que originó la petición.
    */
    private String userEmail; 

    /**
    * Fecha y hora exacta en la que se registró la canción en el sistema.
    */
    private LocalDateTime requestedAt;

    /**
    * Importe abonado por el cliente por esta canción específica.
    * Este valor se recupera de la tabla 'subscription_plans' en el 
    * momento del pago para asegurar que el registro refleje el precio real cobrado.
    */
    private double amountPaid;
    
    /**
    * Constructor por defecto.
    * Inicializa automáticamente la fecha de solicitud al momento actual.
    */
    public Track() {
        this.requestedAt = LocalDateTime.now();
    }

    /**
    * Constructor para facilitar la creación de registros desde el MusicService.
    * * @param spotifyId ID de Spotify.
    * @param title Nombre de la canción.
    * @param artist Artista de la canción.
    * @param userEmail Usuario responsable.
    * @param amount Precio dinámico obtenido de la base de datos.
    */
    public Track(String spotifyId, String title, String artist, String userEmail, double amount) {
        this();
        this.spotifyId = spotifyId;
        this.title = title;
        this.artist = artist;
        this.userEmail = userEmail;
        this.amountPaid = amount;
    }

    // --- GETTERS Y SETTERS ---
    public Long getInternalId() { return internalId; }
    public void setInternalId(Long internalId) { this.internalId = internalId; }
    
    public String getSpotifyId() { return spotifyId; }
    public void setSpotifyId(String spotifyId) { this.spotifyId = spotifyId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(double amountPaid) { this.amountPaid = amountPaid; }
}