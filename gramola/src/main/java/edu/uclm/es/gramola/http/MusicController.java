package edu.uclm.es.gramola.http;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import edu.uclm.es.gramola.services.MusicService;

/**
 * CONTROLADOR API PARA EL HISTORIAL DE MÚSICA.
 * * Este controlador expone los endpoints necesarios para que el frontend pueda 
 * notificar al backend cada vez que una canción es añadida con éxito a la cola 
 * de reproducción tras un pago.
 */
@RestController
@RequestMapping("/music")
@CrossOrigin(origins = {"http://localhost:4200","http://127.0.0.1:4200"}, allowCredentials = "true")
public class MusicController {

    @Autowired
    private MusicService musicService;

    /**
     * Endpoint para registrar una canción en la base de datos tras el pago.
     * * Recibe un objeto JSON con los datos de la canción y del cliente.
     * * @param info Mapa de datos que contiene 'id' (Spotify), 'title', 'artist' y 'email'.
     */
    @PostMapping("/add")
    public void addTrack(@RequestBody Map<String, Object> info) {
        try {
            // 1. Extracción segura de metadatos del cuerpo de la petición.
            // Se utilizan valores por defecto si los campos opcionales no vienen informados.
            String id = info.get("id") != null ? info.get("id").toString() : null;
            String title = info.get("title") != null ? info.get("title").toString() : "Desconocido";
            String artist = info.get("artist") != null ? info.get("artist").toString() : "Desconocido";
            String email = info.get("email") != null ? info.get("email").toString() : "anonimo";

            // 2. Validación de negocio mínima:
            // Sin el identificador de Spotify (id) no se puede realizar el registro.
            if (id == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el ID de Spotify");
            }

            /*LÓGICA DE SINCRONIZACIÓN Y SEGURIDAD:
             * Siguiendo el requisito de no 'hardcodear' precios en el código,
             * el controlador delega la responsabilidad de asignar el precio al MusicService.
             * * El controlador no recibe el precio del frontend (lo cual sería inseguro),
             * sino que el servicio lo consultará directamente en la tabla 'subscription_plans de la base de datos'.
             */
            this.musicService.saveTrack(id, title, artist, email);
            
        } catch (ResponseStatusException rse) {
             // Se re-lanzan las excepciones controladas para que Spring devuelva el código HTTP correcto.
            throw rse;
        } catch (Exception e) {
            // Registro de errores inesperados y devolución de un error 500 genérico.
            System.err.println("[ERROR] Fallo al procesar /music/add: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al registrar la canción");
        }
    }
}