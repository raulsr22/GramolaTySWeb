package edu.uclm.es.gramola.services;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import edu.uclm.es.gramola.dao.TokenDao;
import edu.uclm.es.gramola.dao.UserDao;
import edu.uclm.es.gramola.model.Token;
import edu.uclm.es.gramola.model.User;

/**
 * SERVICIO DE LÓGICA DE NEGOCIO PARA USUARIOS (BARES).
 * * Esta clase es el motor del sistema de identidad y acceso. Gestiona el registro,
 * la autenticación segura, la geolocalización automática del establecimiento y 
 * los flujos de seguridad mediante tokens temporales.
*/
@Service
public class UserService {

    @Autowired
    private UserDao userDao;

    @Autowired
    private TokenDao tokenDao;
    
    // Servicio para la comunicación externa mediante email
    @Autowired
    private EmailService emailService;

    /** Servicio de integración con mapas para el Requisito Extra A (Geolocalización) */
    @Autowired
    private GeocodingService geocodingService;

    /**
     * REGISTRO DE UN NUEVO BAR.
     * * Realiza las siguientes acciones:
     * 1. Valida que el email no esté en uso.
     * 2. Persiste los datos del bar y las claves de Spotify.
     * 3. Captura la firma digital (Requisito Extra C).
     * 4. Obtiene coordenadas GPS reales mediante la dirección (Requisito Extra A).
     * 5. Genera un token de activación y envía el correo de bienvenida.
    */
    @Transactional
    public void register(String bar, String email, String pwd, String clientId, String clientSecret, String address, String signature) { 
        Optional<User> optUser = this.userDao.findById(email);
        if (optUser.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya está registrado");
        }

        // Usuario nuevo
        User user = new User();
        user.setEmail(email);
        user.setBar(bar); 
        user.setPwd(pwd); // El método setPwd de la entidad se encarga de la encriptación SHA-256
        user.setClientId(clientId); 
        user.setClientSecret(clientSecret); 
        user.setAddress(address);
        // Almacenamiento de la firma manuscrita en Base64
        user.setSignature(signature);

            // --- LÓGICA DE GEOLOCALIZACIÓN (Extra A) ---
            if (address != null && !address.isBlank()) {
            // Llamada al servicio externo (Nominatim)
            double[] coords = geocodingService.getCoordinates(address);
            
            if (coords != null) {
                user.setLat(coords[0]);
                user.setLng(coords[1]);
                System.out.println("[GEO] Coordenadas guardadas para " + bar + ": Lat=" + coords[0] + ", Lng=" + coords[1]);
            } else {
                System.err.println("[GEO] No se pudieron obtener coordenadas para la dirección: " + address);
            }
        }

        // --- GESTIÓN DE TOKEN DE ACTIVACIÓN ---
        Token creationToken = new Token(); 
        user.setCreationToken(creationToken);

        // Persistencia inicial en la base de datos
        this.userDao.save(user); 
        
          // --- ENVÍO DE EMAIL DE CONFIRMACIÓN  ---
        try {
            emailService.sendConfirmationEmail(user, creationToken);
        } catch (Exception e) {
            // Manejamos el fallo de envío como un error interno, pero mantenemos el usuario guardado.
            // Esto es crucial para que el flujo de persistencia no se interrumpa.
            System.err.println("Error al enviar email de confirmación a " + email + ". La cuenta se ha creado, pero el correo falló: " + e.getMessage());
        }
        
        System.out.println("[REGISTRO] Bar: " + bar + " registrado.");
    }

    /**
     * PROCESO DE AUTENTICACIÓN (LOGIN).
     * * Verifica las credenciales y asegura que el usuario haya confirmado
     * su correo electrónico antes de permitirle el acceso al panel.
     * * @return El objeto User si las credenciales son válidas y la cuenta está activa.
    */
    @Transactional(readOnly = true)
    public User login(String email, String pwd) {
        // buscamos por ID (email es @Id)
        User user = this.userDao.findById(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No existe el usuario o la contraseña es incorrecta"));

        // encriptamos igual que en register() y comparamos
        String encryptedPwd = user.encryptPassword(pwd); 
        if (!encryptedPwd.equals(user.getPwd())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No existe el usuario o la contraseña es incorrecta");
        }

        // comprobar confirmación del email (token usado)
        Token token = user.getCreationToken(); 
        if (token != null && !token.isUsed()) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE,
                    "El usuario no ha confirmado su email");
        }

        return user;
    }

    /**
     * ELIMINACIÓN DE CUENTA.
    */
    @Transactional
    public void delete(String email) {
        if (!this.userDao.existsById(email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el usuario");
        }
        this.userDao.deleteById(email);
    }

    /**
     * VALIDACIÓN Y CONSUMO DE TOKENS DE SEGURIDAD.
     * * Comprueba que el token coincida, que no haya sido usado previamente
     * y que no haya expirado (límite de 30 minutos).
    */
    @Transactional
    public void confirmToken(String email, String tokenId) {
        // 1) Usuario
        User user = this.userDao.findById(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el usuario"));

        // 2) Token asociado al usuario
        Token userToken = user.getCreationToken();
        if (userToken == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "El usuario no tiene token de creación");
        }

        // 3) Debe coincidir el id
        if (!userToken.getId().equals(tokenId)) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Token incorrecto");
        }

        // 4) Caducidad: 30 minutos
        long ahora = System.currentTimeMillis();
        long caducidadMs = 30L * 60L * 1000L;
        if (userToken.getCreationTime() < (ahora - caducidadMs)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token caducado");
        }

        // 5) Ya usado
        if (userToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token ya usado");
        }

        // 6) Marcar como usado y persistir
        userToken.use();
        this.tokenDao.save(userToken); 
    }

    /**
     * GENERA UN TOKEN PARA EL RESTABLECIMIENTO DE CONTRASEÑA.
    */
    @Transactional
    public String createPasswordResetToken(String email) {
        this.userDao.findById(email)
            .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un usuario con ese email"));

        // Token nuevo (genera id y creationTime en el constructor)
        Token token = new Token();
        this.tokenDao.save(token);

        // Devolvemos el id del token para construir el enlace de reset
        return token.getId();
    }


    /**
     * ENVÍO DE EMAIL DE RECUPERACIÓN.
    */
    public void sendResetPasswordEmail(String email, String link) {
        String subject = "Recuperación de Contraseña - La Gramola";
        String content = "Hola,\n\n"
                + "Hemos recibido una solicitud para restablecer tu contraseña.\n"
                + "Haz clic en el siguiente enlace para crear una nueva:\n\n"
                + link + "\n\n"
                + "Este enlace caducará en 30 minutos.\n"
                + "Si no has sido tú, ignora este mensaje.";
        
        // Llama al método genérico send() de EmailService
        this.emailService.send(email, subject, content);
    }


    /**
     * EJECUTA EL CAMBIO FINAL DE CONTRASEÑA.
     * * Valida la legitimidad del token antes de actualizar el hash en la BD.
    */
    @Transactional
    public void resetPassword(String email, String tokenId, String newPwd) {
        // 1) Usuario
        User user = this.userDao.findById(email)
            .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No existe un usuario con ese email"));

        // 2) Token independiente (reutilizamos la tabla token)
        Token token = this.tokenDao.findById(tokenId)
            .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Token no válido"));

        // 3) Caducidad (mismo criterio que confirmToken: 30 minutos)
        long ahora = System.currentTimeMillis();
        long caducidadMs = 30L * 60L * 1000L;
        if (token.getCreationTime() < (ahora - caducidadMs)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token caducado");
        }

        // 4) Comprobar si ya se usó
        if (token.isUsed()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Token ya usado");
        }

        // 5) Actualizar contraseña (setPwd ya encripta)
        user.setPwd(newPwd);
        this.userDao.save(user);

        // 6) Marcar token como usado
        token.use();
        this.tokenDao.save(token);
    }
}