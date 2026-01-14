package edu.uclm.es.gramola.services;

import edu.uclm.es.gramola.dao.UserDao;
import edu.uclm.es.gramola.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List; 
import java.util.Map;

/**
 * SERVICIO DE INTEGRACIÓN CON LA API DE SPOTIFY.
 * * Esta clase gestiona toda la comunicación con los servidores de Spotify.
 * Se encarga de dos tareas fundamentales:
 * 1. El flujo de autorización OAuth 2.0 (obtención y persistencia de tokens).
 * 2. El consumo de recursos de la API (búsqueda, dispositivos, playlists).
*/
@Service
public class SpotyService {

    @Autowired
    private UserDao userDao;

    /**
     * URL de redirección configurada en el Dashboard de Spotify.
     * Debe coincidir exactamente con la enviada desde el frontend.
    */
    @Value("${app.spotify.redirect-uri}")
    private String redirectUri;

    /** URL oficial para el intercambio de tokens. */
    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
    
    // Nueva URL base para el consumo directo de la API de Spotify
    private String spotifyApiUrl = "https://api.spotify.com/v1";

    /**
     * PASO FINAL DEL FLUJO OAUTH 2.0: INTERCAMBIO DE CÓDIGO POR TOKEN.
     * * Este método recibe el código temporal de Spotify y lo canjea por un 
     * 'Access Token' permanente. Además, persiste dicho token en la base de datos 
     * para que el bar no pierda la conexión al cerrar la sesión.
     * * @param code Código de autorización recibido tras el consentimiento del usuario.
     * @param clientId ID de la aplicación de Spotify del bar.
     * @return Objeto JSON con la respuesta original de Spotify (tokens, expiración).
     */
    public Object getAuthorizationToken(String code, String clientId) {
        
        // 1. Buscamos a los usuarios por su Client ID
        List<User> users = userDao.findByClientId(clientId);
        
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client ID de Spotify no registrado en el sistema.");
        }
        
         // Recuperamos el 'Client Secret' guardado de forma segura en el backend.
        String clientSecret = users.get(0).getClientSecret();
        
        // 2. CONFIGURACIÓN DE CABECERAS (AUTH BASIC):
        // Spotify requiere autenticación mediante Basic Auth (ClientID:ClientSecret en Base64).
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret); 
        
        // 3. CUERPO DE LA PETICIÓN:
        // Definimos los parámetros obligatorios según la documentación de Spotify.
        MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("grant_type", "authorization_code");
        requestBody.add("code", code);
        requestBody.add("redirect_uri", redirectUri); 
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);
        
        // 4. Enviar la petición a Spotify
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Object> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, 
                request, 
                Object.class 
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                
                // 5. PERSISTENCIA DEL TOKEN:
                // Si el intercambio es exitoso, extraemos el access_token y lo guardamos 
                // en todos los perfiles de usuario vinculados a este ClientID.
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseData = (Map<String, Object>) response.getBody();
                    
                    if (responseData != null && responseData.containsKey("access_token")) {
                        String accessToken = (String) responseData.get("access_token");
                        
                        // Recorremos TODOS los usuarios encontrados con ese ClientID
                        for (User u : users) {
                            u.setSpotifyAccessToken(accessToken);
                            this.userDao.save(u); // Actualización en MySQL
                        }
                        
                        System.out.println("[SPOTIFY] Token persistido para " + users.size() + " usuarios con ClientID: " + clientId);
                    }
                } catch (Exception e) {
                    System.err.println("Aviso: No se pudo procesar el token para la BD: " + e.getMessage());
                }

                return response.getBody();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Spotify no devolvió un Access Token válido.");
            }
        } catch (Exception e) {
            System.err.println("Error al comunicarse con Spotify: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error de comunicación con Spotify.");
        }
    }

    // --- MÉTODOS DE CONSUMO DE API (UTILIDADES PARA EL FRONTEND) ---

    /**
     * Construye las cabeceras estándar para peticiones autenticadas de Spotify.
    */

    private HttpHeaders getHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    /**
     * Recupera la lista de dispositivos activos vinculados a la cuenta.
    */
    public Object getDevices(String token) {
        String url = spotifyApiUrl + "/me/player/devices";
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(getHeaders(token));
        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class).getBody();
    }

    /**
     * Obtiene las listas de reproducción del propietario para la música de fondo.
    */
    public Object getPlaylists(String token) {
        String url = spotifyApiUrl + "/me/playlists";
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(getHeaders(token));
        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class).getBody();
    }

    /**
     * Realiza búsquedas de canciones en el catálogo de Spotify.
    */
    public Object searchTracks(String token, String query) {
        String url = spotifyApiUrl + "/search?q=" + query + "&type=track&limit=10";
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(getHeaders(token));
        return restTemplate.exchange(url, HttpMethod.GET, entity, Object.class).getBody();
    }
}