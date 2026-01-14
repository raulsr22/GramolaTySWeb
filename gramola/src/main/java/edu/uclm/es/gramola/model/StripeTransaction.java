package edu.uclm.es.gramola.model;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.util.Map;
import org.json.JSONObject;

/**
 * ENTIDAD PARA EL REGISTRO DE TRANSACCIONES CON STRIPE.
 * * Esta clase representa una intención de pago o un pago completado en el sistema.
 * Es vital para auditar los cobros y para permitir que el frontend recupere el 
 * 'Client Secret' necesario para mostrar el formulario de tarjeta de Stripe.
 */
@Entity
public class StripeTransaction {

     /**
     * Identificador único de la transacción en nuestro sistema.
     * Se genera automáticamente como un UUID para evitar colisiones y 
     * no exponer IDs incrementales por seguridad.
     */
    @Id
    @Column(length = 36)
    private String id;

     /**
     * Almacena el objeto JSON completo devuelto por la API de Stripe.
     * * Se utiliza @Lob y 'TEXT' para que la base de datos pueda 
     * guardar cadenas de texto muy largas sin problemas de compatibilidad.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String data;

     /**
     * Email del usuario que inició el pago.
     * Permite vincular el ingreso de dinero con una cuenta específica.
     */
    @Column(length = 255)
    private String email;

     /**
     * Constructor por defecto. 
     * Inicializa el ID con un identificador único universal (UUID).
     */
    public StripeTransaction() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    // --- GETTERS Y SETTERS ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Convierte la cadena JSON guardada en la base de datos en un Mapa de Java.
     * Facilita al controlador el acceso a los datos sin tener que parsear manualmente.
     * @return Un mapa con las claves y valores del objeto de Stripe o un mapa vacío si hay error.
     */
    public Map<String, Object> getData() {
        try {
            return new JSONObject(this.data != null ? this.data : "{}").toMap();
        } catch (Exception e) {
            return Map.of();
        }
    }

    public void setData(String data) {
        this.data = data;
    }

    /**
     * Sobrecarga de método para guardar datos directamente desde un objeto JSON.
     */
    public void setData(JSONObject jsoData) {
        this.data = jsoData.toString();
    }

    public void setUser(String email) {
        this.email = email;
    }

    public String getUser() {
        return email;
    }

    // -------------------
    // Utilidades opcionales
    // -------------------

    /**
     * Extrae el 'client_secret' del interior del JSON de Stripe.
     * Este client_secret es el que el frontend de Angular necesita para 
     * autorizar el cobro en el navegador del cliente.
     */
    public String getClientSecret() {
        Map<String, Object> map = getData();
        Object secret = map.get("client_secret");
        return secret != null ? secret.toString() : null;
    }

     /**
     * Extrae el identificador oficial del 'PaymentIntent' de Stripe.
     * Comienza habitualmente por "pi_...". Es necesario para consultar 
     * el estado real del pago en los servidores de Stripe desde el backend.
     */
    public String getStripePaymentIntentId() {
        Map<String, Object> map = getData();
        Object idValue = map.get("id");
        return idValue != null ? idValue.toString() : null;
    }
}
