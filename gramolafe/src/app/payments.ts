// src/app/payments.ts
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * ESTRUCTURA DE TRANSACCIÓN DE STRIPE.
 * * Define el objeto que devuelve el servidor al preparar un pago.
 * Contiene el ID interno de la transacción y el objeto 'data' que incluye
 * el 'client_secret' necesario para el Iframe de Stripe.
*/
export interface StripeTransaction {
  id: string;
  data: any;
}

/**
 * SERVICIO DE COMUNICACIÓN FINANCIERA (PAYMENTS SERVICE).
 * * Este servicio centraliza las llamadas a la API relacionadas con la pasarela de pagos.
 * Se encarga de solicitar al servidor la creación de cargos y de notificar cuando
 * el cliente ha completado la operación en su navegador.
*/
@Injectable({ providedIn: 'root' })
export class Payments {
  /** Prefijo opcional para versionado de API (actualmente vacío) */
  private readonly API_PREFIX = '';  
  private readonly baseUrl = 'http://localhost:8080'; // Definimos la URL base del servidor

  constructor(private client: HttpClient) {}

  /**
   * PASO 1: SOLICITUD DE INTENCIÓN DE PAGO (PREPAY).
   * * Informa al backend del deseo de realizar una compra de un plan específico.
   * El backend recuperará el precio oficial de la base de datos basándose en el ID.
   * * @param planId Identificador del plan (ej: "SONG", "MONTHLY", "ANNUAL").
   * @returns Un Observable con la transacción preparada y el secreto de Stripe.
  */
  prepay(planId: string): Observable<StripeTransaction> {
    const url = `${this.baseUrl}${this.API_PREFIX}/payments/prepay`;

    // REQUISITO: Se envía el planId en el cuerpo de una petición POST
    return this.client.post<StripeTransaction>(
      url,
      { planId },
      { withCredentials: true }
    );
  }


  /**
   * PASO 2: CONFIRMACIÓN DEL PAGO (CONFIRM).
   * * Se invoca tras el éxito visual en Stripe para que el backend valide el ingreso 
   * de dinero real en la cuenta y proceda a activar el servicio (suscripción o canción).
   * @param payload Objeto con el 'transactionId' y el identificador del usuario.
  */
  confirm(payload: any): Observable<void> {
    return this.client.post<void>(
      `http://localhost:8080${this.API_PREFIX}/payments/confirm`,
      payload,
      { withCredentials: true }
    );
  }
}