package edu.uclm.es.gramola.model;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ENTIDAD PARA LA GESTIÓN DINÁMICA DE PRECIOS.
 * * Esta clase es clave para cumplir con el requisito técnico de la práctica que 
 * prohíbe el "hardcoding" (escribir valores fijos) de los precios en el código.
 * * Representa un producto o servicio que puede ser cobrado en el sistema, como 
 * una suscripción mensual para el bar o el pago individual de una canción para el cliente.
 */
@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    
     /**
     * Identificador único del plan.
     * Se utilizan códigos semánticos como "MONTHLY", "ANNUAL" o "SONG" 
     * para facilitar las búsquedas desde los servicios de pago.
     */
    @Id
    private String id; 
    
     /**
     * Importe del plan en euros. 
     * Al estar en la base de datos, un administrador puede cambiar el precio 
     * de la gramola sin necesidad de recompilar ni desplegar de nuevo el servidor.
     */
    private double price;

     /**
     * Texto informativo que explica en qué consiste el cobro.
     * Este valor se envía a Stripe para que el usuario sepa qué está pagando 
     * en el recibo bancario.
     */
    private String description;

     /**
     * Constructor por defecto requerido por JPA para la instanciación automática 
     * desde la base de datos.
     */
    public SubscriptionPlan() {}

    /**
     * Constructor parametrizado.
     * Útil para la inicialización de datos durante el arranque del sistema.
     * * @param id Código del plan.
     * @param price Importe oficial.
     * @param description Detalle del servicio.
     */
    public SubscriptionPlan(String id, double price, String description) {
        this.id = id;
        this.price = price;
        this.description = description;
    }

    // --- GETTERS Y SETTERS ---
    // Permiten el acceso y modificación controlada de los atributos de la entidad.
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}