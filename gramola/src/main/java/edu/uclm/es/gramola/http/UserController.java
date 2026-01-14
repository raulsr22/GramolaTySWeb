package edu.uclm.es.gramola.http;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import edu.uclm.es.gramola.model.User;
import edu.uclm.es.gramola.services.UserService;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CONTROLADOR API PARA LA GESTIÓN DE USUARIOS Y SEGURIDAD.
 * * Este controlador expone los servicios de "Identidad y Acceso" para los propietarios de bares.
 * Gestiona el ciclo de vida completo del usuario: registro inicial, validación de cuenta por email,
 * inicio de sesión persistente y recuperación de contraseñas olvidadas.
 */
@RestController
@RequestMapping("/users")
@CrossOrigin(origins = {"http://localhost:4200","http://127.0.0.1:4200"}, allowCredentials = "true") 
public class UserController {

    @Autowired
    private UserService service;

    @Value("${app.front-url}")
    private String frontUrl;

     /**
     * REGISTRO DE UN NUEVO ESTABLECIMIENTO.
     * * Recibe los datos del formulario de registro, incluyendo las claves de Spotify
     * y la firma digital del propietario (Requisito Extra).
     * * @param body Mapa con los 8 campos necesarios (bar, email, pwd1/2, spotify keys, address, signature).
     * @return 204 No Content si el proceso de guardado y envío de email de confirmación fue exitoso.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody Map<String, String> body) {
        
        // 1. Extracción de parámetros enviados desde Angular
        String bar          = body.get("bar");
        String email        = body.get("email");
        String pwd1         = body.get("pwd1");
        String pwd2         = body.get("pwd2");
        String clientId     = body.get("clientId");
        String clientSecret = body.get("clientSecret");
        String address      = body.get("address");
        String signature    = body.get("signature"); // 🚨 Extra C

        // 2. Validaciones de integridad en el lado del servidor (Capa de Seguridad)
        if (bar == null || bar.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "El nombre del bar es obligatorio");
        }

        if (signature == null || signature.isBlank()) {
             throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "La firma del propietario es obligatoria.");
        }

        if (address == null || address.isBlank()) {
             throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "La dirección del bar es obligatoria para la geolocalización.");
        }
        
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Las claves de Spotify (Client Id/Secret) son obligatorias");
        }

        if (pwd1 == null || pwd2 == null || !pwd1.equals(pwd2)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Las contraseñas no coinciden");
        }

        if (pwd1.length() < 8) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "La contraseña debe tener al menos 8 caracteres");
        }

        if (email == null || !email.contains("@") || !email.contains(".")) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Dirección de email inválida");
        }
        
        // 3. Delegación al servicio de negocio para persistencia y envío de correo
        this.service.register(bar, email, pwd1, clientId, clientSecret, address, signature); 
        return ResponseEntity.noContent().build(); // 204
    }

     /**
     * INICIO DE SESIÓN (LOGIN).
     * * Valida las credenciales y devuelve los datos necesarios para que el frontend
     * funcione, incluyendo las coordenadas para el control de distancia.
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String pwd   = body.get("pwd");
        User user = this.service.login(email, pwd);
        
        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("clientId", user.getClientId());
        // Enviamos las coordenadas para validar el radio de 100m en el front
        response.put("lat", user.getLat());
        response.put("lng", user.getLng());

        // Devolvemos la firma guardada para confirmación visual en el panel
        response.put("signature", user.getSignature());
        
        return response;
    }

     /**
     * ELIMINACIÓN DE CUENTA.
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Void> delete(@RequestParam String email) {
        this.service.delete(email);
        return ResponseEntity.noContent().build();
    }

    /**
     * CONFIRMACIÓN DE TOKEN Y REDIRECCIÓN AUTOMÁTICA.
     * * Este método se activa cuando el usuario pulsa el enlace del email de bienvenida.
     * * Redirige directamente a la pantalla de pago.
     */
    @GetMapping("/confirmToken/{email}")
    public void confirmToken(@PathVariable String email,
                             @RequestParam String token,
                             HttpServletResponse response) { 
        try {
            // 1. Validar el token y activar la cuenta en el sistema
            this.service.confirmToken(email, token);
            
            // 2. Construir URL de redirección a la pasarela de pagos con el token de seguridad
            String url = frontUrl + "/payments?token=" + URLEncoder.encode(token, "UTF-8");
            
            // 3. Ejecutar redirección HTTP 302
            response.setStatus(302);
            response.setHeader("Location", url);
        } catch (UnsupportedEncodingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fallo al codificar la URL.");
        } catch (ResponseStatusException rse) {
            throw rse; 
        } catch (Exception e) {
            // Capturar cualquier otra excepción no esperada del servicio.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado en la confirmación: " + e.getMessage());
        }
    }

    /** CONFIRM 
     * Confirma el usuario a partir de email + token.
     */
    @GetMapping("/confirm")
    public Map<String, String> confirm(@RequestParam String email,
                                       @RequestParam String token) {
        try {
            this.service.confirmToken(email, token); 
            return Map.of("status", "ok", "message", "Usuario confirmado");
        } catch (ResponseStatusException rse) {
            throw rse; 
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }


     /**
     * SOLICITUD DE RECUPERACIÓN DE CONTRASEÑA.
     * * Genera un token de reset y envía un email con un enlace único al usuario.
     */
    @PostMapping("/password/token")
    public Map<String, String> createResetToken(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String token = this.service.createPasswordResetToken(email);

        try {
            String resetUrl = frontUrl
                    + "/reset-password?email="
                    + URLEncoder.encode(email, "UTF-8")
                    + "&token="
                    + URLEncoder.encode(token, "UTF-8");

            System.out.println("[RESET LINK] " + resetUrl);

            // Enviar el email real con el link generado
            this.service.sendResetPasswordEmail(email, resetUrl);

            return Map.of(
                    "status", "ok",
                    "message", "Si el usuario existe, se ha enviado un enlace.",
                    "resetUrl", resetUrl
            );
        } catch (UnsupportedEncodingException e) {
             throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Fallo al codificar la URL de reset.");
        }
    }


     /**
     * ACTUALIZACIÓN DE CONTRASEÑA (RESET).
     * * Procesa el cambio final de contraseña validando que el token de recuperación sea legítimo.
     */
    @PostMapping("/password/reset")
    public Map<String, String> resetPassword(@RequestBody Map<String, String> body) {

        String email  = body.get("email");
        String token  = body.get("token");
        String newPwd = body.get("newPwd");

        this.service.resetPassword(email, token, newPwd);

        return Map.of(
                "status", "ok",
                "message", "Contraseña actualizada correctamente."
        );
    }
}