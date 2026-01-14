package edu.uclm.es.gramola.dao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import edu.uclm.es.gramola.model.StripeTransaction;

/**
 * REPOSITORIO DE ACCESO A DATOS (DAO) PARA TRANSACCIONES.
 * * Esta interfaz utiliza Spring Data JPA para gestionar la persistencia de los pagos 
 * realizados a través de Stripe. Al extender de JpaRepository, obtenemos automáticamente 
 * las operaciones básicas (guardar, eliminar, buscar por ID) sin escribir SQL.
 */
@Repository
public interface StripeTransactionDao extends JpaRepository<StripeTransaction, String> {

    /**
     * Busca una transacción específica asegurando que pertenece a un usuario concreto.
     * * @param id El identificador único (UUID) de la transacción generada en el sistema.
     * @param email El correo electrónico del propietario o cliente asociado al pago.
     * @return Un Optional que contiene la transacción si existe y coincide con el email, 
     * proporcionando una capa extra de seguridad para evitar que un usuario 
     * consulte pagos ajenos.
     */
    Optional<StripeTransaction> findByIdAndEmail(String id, String email);

    /**
     * Comprueba de forma rápida si una transacción existe y está vinculada a un email.
     * * @param id Identificador de la transacción.
     * @param email Correo electrónico a validar.
     * @return true si la combinación ID-Email existe en la base de datos, false en caso contrario.
     * Es útil para validaciones previas antes de realizar operaciones pesadas.
     */
    boolean existsByIdAndEmail(String id, String email);

    
}
