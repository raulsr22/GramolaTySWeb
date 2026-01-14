package edu.uclm.es.gramola.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import edu.uclm.es.gramola.model.SubscriptionPlan;

/**
 * REPOSITORIO PARA PLANES DE SUSCRIPCIÓN Y PRECIOS.
 * * Esta interfaz es fundamental para cumplir con el requisito de diseño del sistema
 * que prohíbe el uso de precios fijos ("hardcodeados") en el código. 
 * * Al extender de CrudRepository, el sistema puede consultar en tiempo real los 
 * importes de las suscripciones (Mensual, Anual) y de las canciones sueltas 
 * directamente desde la tabla 'subscription_plans'.
 */
@Repository
public interface SubscriptionPlanDao extends CrudRepository<SubscriptionPlan, String> {
    /**
     * El repositorio utiliza un String como identificador (ID), que corresponde
     * a los códigos de plan definidos (ej: "SONG", "MONTHLY", "ANNUAL").
     * * Hereda automáticamente métodos como findById(), findAll() y save(), 
     * permitiendo que el PaymentService recupere el precio oficial antes 
     * de generar cualquier intención de pago en Stripe.
     */
}