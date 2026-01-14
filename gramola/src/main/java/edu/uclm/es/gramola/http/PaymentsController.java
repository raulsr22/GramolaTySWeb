package edu.uclm.es.gramola.http;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stripe.Stripe;
import edu.uclm.es.gramola.model.StripeTransaction;
import edu.uclm.es.gramola.model.SubscriptionPlan;
import edu.uclm.es.gramola.model.User;
import edu.uclm.es.gramola.services.PaymentService;

/**
 * CONTROLADOR API PARA LA PASARELA DE PAGOS (STRIPE).
 * * Este controlador es el puente entre el Frontend de Angular, nuestro Backend 
 * y los servidores de Stripe. Gestiona tanto los pagos de suscripción de los 
 * dueños de bares como los pagos de los clientes por cada canción.
 */
@RestController
@RequestMapping("payments")
@CrossOrigin(
    origins = { "http://localhost:4200", "http://127.0.0.1:4200" },
    allowCredentials = "true"
)
public class PaymentsController {

    @Autowired
    private PaymentService service;

    /**
     * 1) OBTENER PLANES: Recupera la lista de precios oficiales.
     * * REQUISITO TÉCNICO: Los precios no están escritos en el código.
     * Este endpoint permite al frontend dibujar la interfaz basándose exclusivamente
     * en lo que hay guardado en la tabla 'subscription_plans' de MySQL.
     * * @return Lista de objetos SubscriptionPlan (ID, Precio, Descripción).
     */
    @GetMapping("/plans")
    public List<SubscriptionPlan> getPlans() {
        return this.service.getAvailablePlans();
    }

    /**
     * 2) PREPARAR PAGO (PREPAY): Inicia la intención de cobro.
     * * Recibe una solicitud con un 'planId'. El backend consulta el precio real 
     * en la base de datos y contacta con Stripe para generar un 'Client Secret'.
     * Este secreto permitirá al frontend mostrar el formulario de tarjeta de forma segura.
     * * @param info Mapa que contiene el 'planId' solicitado (ej: "SONG", "MONTHLY").
     * @return Objeto StripeTransaction con los datos de la intención de pago.
     */
    @PostMapping("/prepay")
    public StripeTransaction prepay(@RequestBody Map<String, Object> info) {
        try {
            if (info.get("planId") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el ID del plan");
            }
            
            String planId = info.get("planId").toString();
            // El servicio se encarga de buscar el precio en la BD según este ID
            return this.service.prepay(planId); 
            
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al preparar el pago: " + e.getMessage());
        }
    }
    
    /**
     * 3) CONFIRMAR PAGO (CONFIRM): Valida el éxito de la operación.
     * * Se llama una vez que el cliente ha introducido su tarjeta y Stripe ha 
     * procesado el dinero. El backend verifica la veracidad del pago mediante 
     * el 'transactionId' y procede a activar la suscripción o registrar la canción.
     * * @param finalData Mapa con 'transactionId' y el identificador del usuario ('token' o 'email').
     * @return Respuesta de confirmación para redirigir al usuario en el frontend.
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> finalData) {
        try {
            String transactionId = (String) finalData.get("transactionId");
            String userToken = (String) finalData.get("token"); // Puede ser el email o el token de registro
            
            if (transactionId == null) {
                 throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el ID de transacción");
            }
            
            // Verificamos que la transacción exista en nuestra base de datos
            StripeTransaction transaction = this.service.findTransaction(transactionId);
            if (transaction == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción no registrada en el sistema");
            }

            // El servicio contacta con Stripe para verificar el estado 'succeeded' y actualiza al User
            User user = this.service.confirmTransaction(transaction, userToken);

            // Construcción de la respuesta de éxito
            Map<String, Object> res = new HashMap<>();
            res.put("status", "succeeded");
            res.put("email", user.getEmail());
            res.put("message", "Operación confirmada y registrada en el historial");
            
            return res;

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error en la confirmación: " + e.getMessage());
        }
    }
    
    /**
     * ENDPOINT DE DIAGNÓSTICO: Verifica la integración.
     * * Útil durante el desarrollo para comprobar si las claves de Stripe (Secret Key)
     * están correctamente configuradas y el servidor tiene salida a internet.
     */
    @GetMapping("/diag")
    public Map<String, Object> diag() {
        Map<String, Object> out = new HashMap<>();
        try {
            out.put("stripeConfigured", Stripe.apiKey != null && !Stripe.apiKey.isEmpty());
            var acct = com.stripe.model.Account.retrieve();
            out.put("stripeOk", true);
            out.put("accountId", acct.getId());
        } catch (Exception e) {
            out.put("stripeOk", false);
            out.put("error", e.getMessage());
        }
        return out;
    }
}