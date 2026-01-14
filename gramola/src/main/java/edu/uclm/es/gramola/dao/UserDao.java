package edu.uclm.es.gramola.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import edu.uclm.es.gramola.model.User;
import java.util.List; 

/**
 * REPOSITORIO DE GESTIÓN DE ESTABLECIMIENTOS (USUARIOS).
 * * Esta interfaz es el núcleo del sistema de identidad. Utiliza Spring Data JPA
 * para gestionar la persistencia de los propietarios de los bares, sus credenciales
 * y sus configuraciones de Spotify.
 */
@Repository
public interface UserDao extends JpaRepository<User, String> {

     /**
     * Busca un usuario por su correo electrónico.
     * @param email Identificador único del usuario.
     * @return Un Optional con el usuario si existe.
     */
    Optional<User> findByEmail(String email);

     /**
     * Localiza a un usuario a través del ID de su token de creación.
     * Es fundamental para el proceso de confirmación de registro: cuando el usuario
     * pulsa el link del email, el sistema busca quién posee ese token para activar la cuenta.
     * @param creationTokenId UUID del token enviado por correo.
     * @return El usuario propietario de dicho token.
     */
    Optional<User> findByCreationTokenId(String creationTokenId);

     /**
     * Verifica la existencia de un email en el sistema.
     * Se utiliza en el formulario de registro para evitar duplicados antes de intentar la inserción.
     */
    boolean existsByEmail(String email);

     /**
     * Este método permite localizar a los usuarios basándose en su 'Client ID' de Spotify.
     * Es vital durante el "Callback" de Spotify: cuando Spotify nos devuelve un código 
     * de autorización, solo nos envía el ClientID. Gracias a este método, el SpotyService 
     * puede encontrar al usuario en nuestra base de datos y guardarle el 'Access Token' 
     * correspondiente para que el bar pueda reproducir música.
     * @param clientId El ID de aplicación proporcionado por el Spotify Developer Dashboard.
     * @return Lista de usuarios que comparten ese ID de integración.
     */
     List<User> findByClientId(String clientId);
}
