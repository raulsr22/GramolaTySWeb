package edu.uclm.es.gramola.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import edu.uclm.es.gramola.dao.StripeTransactionDao;
import edu.uclm.es.gramola.dao.SubscriptionPlanDao;
import edu.uclm.es.gramola.dao.UserDao;
import edu.uclm.es.gramola.model.StripeTransaction;
import edu.uclm.es.gramola.model.SubscriptionPlan;
import edu.uclm.es.gramola.model.User;
import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * SERVICIO CENTRAL DE PAGOS E INTEGRACIÓN CON STRIPE.
 * * Esta clase gestiona el flujo financiero del sistema. Se encarga de:
 * 1. Inicializar los precios en la base de datos (evitando hardcoding).
 * 2. Preparar intenciones de pago (PaymentIntents) comunicándose con los servidores de Stripe.
 * 3. Confirmar la validez de las transacciones antes de activar servicios.
*/
@Service
public class PaymentService {

    @Value("${stripe.secret}")
    private String stripeSecret;

    @Autowired
    private SubscriptionPlanDao planDao;

    @Autowired
    private StripeTransactionDao dao;

    @Autowired
    private UserDao userDao;

    /**
     * CONFIGURACIÓN INICIAL DEL SERVICIO.
     * Se ejecuta automáticamente al arrancar la aplicación (@PostConstruct).
    */
    @PostConstruct
    void initStripe() {
        // Configuramos la clave secreta para todas las llamadas a la SDK de Stripe
        Stripe.apiKey = stripeSecret; 
        // Log de seguridad para verificar la carga de la clave en el arranque
        System.out.println("[Stripe] key prefix=" + 
            (stripeSecret == null ? "null" : stripeSecret.substring(0, Math.min(9, stripeSecret.length()))));
    


        /**
         * REQUISITO: PRECIOS DINÁMICOS.
         * Si la tabla de planes está vacía, la inicializamos con los valores por defecto.
         * Esto garantiza que los precios nunca estén escritos en la lógica de negocio,
         * permitiendo su modificación desde la base de datos.
         */
        if (planDao.count() == 0) {
            planDao.save(new SubscriptionPlan("SONG", 1.0, "Pago por canción única"));
            planDao.save(new SubscriptionPlan("MONTHLY", 10.0, "Suscripción Mensual"));
            planDao.save(new SubscriptionPlan("ANNUAL", 15.0, "Suscripción Anual"));
            System.out.println("[DB] Planes de suscripción inicializados.");}
    }

        /**
        * Recupera todos los planes disponibles para que el Frontend haga la interfaz.
        */
        public List<SubscriptionPlan> getAvailablePlans() {
            return (List<SubscriptionPlan>) planDao.findAll();
    }

    /**
     * PASO 1: PREPARACIÓN DEL PAGO (PREPAY).
     * Crea un PaymentIntent en los servidores de Stripe.
     * * @param planId Identificador del producto ("SONG", "MONTHLY", etc.)
     * @return Objeto de transacción local que contiene el 'client_secret' de Stripe.
     */
    public StripeTransaction prepay(String planId) throws StripeException { 
        
        // 1. Consultamos el precio real en la Base de Datos (Requisito de no hardcoding)
        SubscriptionPlan plan = planDao.findById(planId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El plan de pago no existe en la BD"));

        // Stripe maneja los importes en céntimos (ej: 1.00€ = 100 céntimos)
        long amountCents = (long) (plan.getPrice() * 100);

        // 2. Configuramos los parámetros para Stripe
        PaymentIntentCreateParams createParams = new PaymentIntentCreateParams.Builder()
                .setCurrency("eur")
                .setAmount(amountCents)
                .setDescription(plan.getDescription())
                .build();

        // 3. Solicitamos la creación a Stripe
        PaymentIntent intent = PaymentIntent.create(createParams);

        // 4. Registramos la intención en nuestra base de datos local para auditoría
        StripeTransaction st = new StripeTransaction();
        st.setData(intent.toJson());
        dao.save(st);
        
        return st;
    }

    /**
     * PASO 2: CONFIRMACIÓN DE LA TRANSACCIÓN.
     * Verifica que el pago se haya realizado efectivamente en Stripe antes de dar por buena la operación.
     * * @param tx La transacción local que queremos validar.
     * @param userTokenOrEmail Identificador enviado desde el cliente (puede ser email o token de registro).
     * @return El usuario asociado al pago tras la validación.
    */
    public User confirmTransaction(StripeTransaction tx, String userTokenOrEmail) throws StripeException {

        // 1. Recuperamos el ID oficial de Stripe guardado en nuestra entidad
        String paymentIntentId = tx.getStripePaymentIntentId(); 
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("No se encuentra el id del PaymentIntent");
        }

        // 2. Consultamos el estado REAL en los servidores de Stripe (Verificación de Seguridad)
        PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId);
        String status = pi.getStatus();

        // Solo aceptamos pagos que hayan finalizado con éxito
        boolean ok = "succeeded".equalsIgnoreCase(status) || "requires_capture".equalsIgnoreCase(status);
        if (!ok) {
            throw new IllegalStateException("El pago no está completado. Estado: " + status);
        }

        // 3. Resolvemos la identidad del pagador
        // Esto es necesario porque al confirmar registro recibimos un TOKEN, pero en login recibimos el EMAIL.
        String email = resolveEmail(userTokenOrEmail);
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("No se pudo resolver el usuario.");
        }

        // 4. Actualizamos nuestra base de datos con la respuesta final de Stripe
        tx.setData(pi.toJson());
        tx.setUser(email);
        dao.save(tx);
        
        // 5. Devolvemos el usuario para que el controlador pueda informarle del éxito
        User user = userDao.findById(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        long amountPaid = pi.getAmount(); 
        
        System.out.println("✅ [PAGO CONFIRMADO] Usuario: " + email + " | Cantidad: " + (amountPaid/100.0) + "€");

        return user; 
    }

    /**
     * HELPER: RESOLUCIÓN DE IDENTIDAD.
     * Permite identificar a un usuario ya sea por su email directo o por un token de confirmación de cuenta.
    */
    private String resolveEmail(String tokenOrEmail) {
        if (tokenOrEmail == null) return null;
        if (tokenOrEmail.contains("@")) return tokenOrEmail; // Es un email directo

        try {
            // Si no contiene '@', asumimos que es un token de confirmación de registro
            return userDao.findByCreationTokenId(tokenOrEmail)
                    .map(u -> {
                        try { return (String) u.getClass().getMethod("getEmail").invoke(u); } 
                        catch (Exception e) { return null; }
                    }).orElse(null);
        } catch (Exception e) {
            return tokenOrEmail; // Fallback por si acaso
        }
    }

    /**
     * Busca una transacción específica en nuestro registro local por su UUID.
    */
    public StripeTransaction findTransaction(String id) {
        return dao.findById(id).orElse(null);
    }
}