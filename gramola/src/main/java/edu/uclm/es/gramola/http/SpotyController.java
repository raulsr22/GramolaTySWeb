package edu.uclm.es.gramola.http;
import edu.uclm.es.gramola.services.SpotyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * CONTROLADOR API PARA LA INTEGRACIÓN CON SPOTIFY.
 * * Este controlador gestiona el enlace final del protocolo OAuth 2.0.
 * Es el punto de entrada al que llama el Frontend una vez que el propietario del bar
 * ha aceptado los permisos en la página oficial de Spotify.
 */
@RestController
@RequestMapping("/spoti")
@CrossOrigin(origins = {"http://localhost:4200","http://127.0.0.1:4200"}, allowCredentials = "true")
public class SpotyController {

    @Autowired
    private SpotyService service;

    /**
     * INTERCAMBIO DE CÓDIGO POR TOKEN DE ACCESO.
     * * Este endpoint es invocado por el componente 'Callback' de Angular. 
     * Spotify redirige al usuario a nuestro frontend con un 'code' temporal. 
     * Por seguridad, ese código debe enviarse a nuestro backend para que este lo 
     * intercambie por un 'Access Token' real usando el 'Client Secret' (que nunca debe estar en el front).
     * * @param code El código de autorización temporal proporcionado por Spotify.
     * @param clientId El Identificador de Cliente del bar para saber a qué cuenta vincular el token.
     * @return Un objeto (JSON) que contiene el access_token necesario para reproducir música, 
     * el token de refresco y el tiempo de expiración.
     */
    @GetMapping("/getAuthorizationToken")
    public Object getAuthorizationToken(@RequestParam String code, @RequestParam String clientId) {
        /*
         * La lógica delegada al SpotyService realiza tres acciones críticas:
         * 1. Recupera el 'Client Secret' de la base de datos asociado a ese clientId.
         * 2. Realiza la petición POST a Spotify para obtener los tokens.
         * 3. Persiste el token resultante en la base de datos para que el bar 
         * permanezca conectado incluso si se reinicia la aplicación.
         */
        return service.getAuthorizationToken(code, clientId);
    }
}