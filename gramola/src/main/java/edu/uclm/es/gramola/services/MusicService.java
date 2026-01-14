package edu.uclm.es.gramola.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import edu.uclm.es.gramola.dao.SubscriptionPlanDao;
import edu.uclm.es.gramola.dao.TrackDao;
import edu.uclm.es.gramola.model.SubscriptionPlan;
import edu.uclm.es.gramola.model.Track;

/**
 * SERVICIO DE GESTIÓN DEL HISTORIAL MUSICAL.
 * * Esta clase se encarga de la lógica de persistencia de las canciones que son
 * enviadas a la cola de reproducción. Asegura que cada petición quede registrada
 * correctamente para su posterior auditoría o visualización por parte del bar.
*/
@Service
public class MusicService {
    
    @Autowired
    private TrackDao trackDao;
    
    @Autowired
    private SubscriptionPlanDao planDao;

    /**
     * REGISTRA UNA CANCIÓN EN EL HISTORIAL (TABLA TRACKS).
     * * Este método se ejecuta bajo una transacción para asegurar que la lectura 
     * del precio y la inserción del registro sean atómicas.
     * * REQUISITO CLAVE: CUMPLIMIENTO DE PRECIOS DINÁMICOS.
     * Siguiendo el enunciado, el precio no está escrito en el código. El servicio
     * busca en la tabla 'subscription_plans' el coste actual de una canción ("SONG")
     * antes de guardar el registro.
     * * @param spotifyId Identificador único de la canción en Spotify.
     * @param title     Nombre de la canción.
     * @param artist    Nombre del artista o grupo.
     * @param userEmail Email del cliente que ha realizado el pago.
    */
    @Transactional
    public void saveTrack(String spotifyId, String title, String artist, String userEmail) {
        // 1. CONSULTA DINÁMICA DE PRECIO:
        // Buscamos el objeto de plan con ID "SONG" para conocer su precio actual en BD.
        SubscriptionPlan songPlan = planDao.findById("SONG")
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se ha definido el precio de las canciones en la BD"));

        double price = songPlan.getPrice();

        // 2. CREACIÓN DEL REGISTRO:
        // Se instancia la entidad Track incluyendo el importe real recuperado de la BD.
        Track track = new Track(spotifyId, title, artist, userEmail, price);
        
        // 3. PERSISTENCIA:
        // Se guarda en la tabla 'tracks' de MySQL.
        this.trackDao.save(track);
        
        System.out.println("[DB] Registrada canción: " + title + " | Importe: " + price + "€");
    }
}