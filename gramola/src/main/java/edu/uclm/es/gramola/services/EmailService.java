package edu.uclm.es.gramola.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import edu.uclm.es.gramola.model.Token;
import edu.uclm.es.gramola.model.User;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

/**
 * SERVICIO PARA EL ENVÍO DE CORREOS ELECTRÓNICOS (SMTP).
 * * Esta clase centraliza todas las comunicaciones salientes del sistema.
 * Utiliza el framework JavaMailSender de Spring para conectar con el servidor 
 * de correo (ej: Gmail) y enviar notificaciones críticas a los usuarios.
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender; 

    /**
     * Dirección de correo configurada como remitente en application.properties.
    */
    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * URL base del frontend para construir enlaces de navegación.
    */
    @Value("${app.front-url}")
    private String frontUrl; 

    
    /**
     * MÉTODO GENÉRICO DE ENVÍO.
     * * Se utiliza para mensajes sencillos de texto plano, como las instrucciones 
     * de recuperación de contraseña.
     * * @param to Destinatario.
     * @param subject Asunto del mensaje.
     * @param content Cuerpo del correo.
    */
    public void send(String to, String subject, String content) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(senderEmail);
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(content);

            mailSender.send(mail);
            System.out.println("[SMTP] Email enviado correctamente a: " + to);
        } catch (Exception e) {
            // Se registra el error en consola pero no se interrumpe el flujo principal
            // para evitar que un fallo en el servidor de correo bloquee la aplicación.
            System.err.println("SMTP ERROR: No se pudo enviar el email a " + to + ". Error: " + e.getMessage());
        }
    }
    
    /**
     * ENVÍO DE EMAIL DE CONFIRMACIÓN DE CUENTA (REGISTRO).
     * * Este método es fundamental. Construye un enlace 
     * dinámico que apunta a nuestro backend para validar la identidad del propietario.
     * * @param user El usuario recién registrado.
     * @param token El token de seguridad único generado para esta operación.
     * @throws Exception Si ocurre un fallo crítico que requiera notificación al usuario.
    */
    public void sendConfirmationEmail(User user, Token token) throws Exception {
        try {
            final String encoding = "UTF-8";
            
            // 1. Codificación de seguridad de los parámetros de la URL.
            // Es vital usar URLEncoder para que caracteres especiales en el email 
            // no rompan el enlace HTTP.
            String encodedEmail = URLEncoder.encode(user.getEmail(), encoding);
            String encodedToken = URLEncoder.encode(token.getId(), encoding);
            
            // 2. Construcción del enlace de confirmación.
            // El link apunta al endpoint del controlador del backend que procesa el token.
            String confirmationLink = "http://localhost:8080/users/confirmToken/" 
                                    + encodedEmail
                                    + "?token=" + encodedToken;
            
            // Imprime el link de forma CLARA en la consola
            System.out.println("\n=======================================================");
            System.out.println(">>> COPIA ESTE LINK PARA CONFIRMAR: " + confirmationLink);
            System.out.println("=======================================================\n");


            // 3. Composición del mensaje de bienvenida.
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(senderEmail);
            mail.setTo(user.getEmail());
            mail.setSubject("Gramola: Confirma tu cuenta");

            String content = "Hola " + user.getBar() + ",\n\n"
                           + "Gracias por registrarte. Haz clic para continuar con el pago:\n\n"
                           + confirmationLink;
                           
            
            mail.setText(content);

            // 4. Ejecución del envío.
            mailSender.send(mail);
            System.out.println("[SMTP] Confirmación email enviado a: " + user.getEmail());

        } catch (UnsupportedEncodingException e) {
            // Este catch maneja el error de codificación que ocurre si "UTF-8" no es válido.
            System.err.println("SMTP ERROR: Fallo al codificar URL: " + e.getMessage());
            throw new RuntimeException("Fallo interno al preparar el email (Codificación).", e);
        } catch (Exception e) {
            // Este catch maneja los errores de MailException que lanza mailSender.send() 
            System.err.println("SMTP ERROR: Fallo al enviar el email. Revisa las credenciales SMTP. Error: " + e.getMessage());
            // Lanzamos la excepción para que UserService la maneje
            throw new RuntimeException("Fallo al enviar el email (Credenciales/Servidor SMTP).", e); 
        }
    }
}