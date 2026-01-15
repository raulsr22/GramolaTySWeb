import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpClientModule } from '@angular/common/http'; 

/**
 * DECLARACIÓN GLOBAL DE STRIPE.
 * El script de Stripe se carga habitualmente en el index.html.
*/
declare const Stripe: any;

/** INTERFACES DE DATOS: Aseguran que la comunicación con el Backend sea tipada */
interface SubscriptionPlan { 
  id: string; 
  price: number; 
  description: string; 
}

interface StripeTransaction { 
  id: string; 
  data: any; 
}

/**
 * COMPONENTE DE GESTIÓN DE PAGOS.
 * * Este componente es el centro financiero de "La Gramola". Gestiona:
 * 1. El pago inicial de suscripción tras confirmar el registro del bar.
 * 2. El pago por canción que realizan los clientes.
 * * Sigue estrictamente el requisito de NO 'hardcodear' precios en el cliente.
*/
@Component({
  selector: 'app-payment',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './payments.html',
  styleUrls: ['./payments.css'],
})
export class PaymentComponent implements OnInit, OnDestroy {
   /** * CONFIGURACIÓN DE STRIPE.
   * Se utiliza la clave pública de prueba. La lógica de seguridad real (Secret Key)
   * reside exclusivamente en el backend de Spring Boot.
   */
  private stripe = Stripe('pk_test_51SIV1lI7tP0Sy3na2pLaJTQdS9pmMJkVLbPP46pPGMJA4fftfUjgdI7puoLSPbJgfXVstVuKYcFkwFCLkJdTn5pH00Y6V2KizQ');
  
  private http = inject(HttpClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  /** Referencias a los elementos dinámicos de la SDK de Stripe */
  private elements: any;
  private card: any;

  // --- ESTADOS DE CONTROL DE FLUJO ---
  loading = false;      // Mientras se preparan los planes o la intención inicial
  processing = false;   // Mientras Stripe procesa el cargo bancario
  succeeded = false;    // Cuando la operación es confirmada por el backend
  showCardForm = false; // Control de visibilidad del Iframe de tarjeta
  cardError = '';       // Mensajes de error de la pasarela (fondos, caducidad, etc.)

  // Datos dinámicos de la Base de Datos
  availablePlans: SubscriptionPlan[] = [];
  selectedPlanId: string = 'MONTHLY'; // Plan por defecto para bares
  paymentType: 'subscription' | 'song' = 'subscription'; 
  
  /** Datos de la transacción temporal generada en el backend */
  transactionDetails?: StripeTransaction;
  /** Token de seguridad recibido desde el email de confirmación */
  token?: string; 
  /** Identificador persistente del bar en sesión */
  userEmail: string | null = null;
  /** Dirección a la que volver tras el éxito */
  returnUrl: string = '/music'; 

  /**
   * INICIALIZACIÓN DEL COMPONENTE.
   * Analiza el contexto de la llamada (si viene de un registro o de la gramola)
   * y activa la carga de precios desde la base de datos.
  */
  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.token = params.get('token') ?? undefined;
    // PERSISTENCIA: Recuperamos la sesión para saber quién está pagando
    this.userEmail = localStorage.getItem('userEmail');
    
    // Configuramos la URL de retorno si viene especificada (ej: desde la gramola)
    if (params.get('returnUrl')) {
      this.returnUrl = params.get('returnUrl')!;
    }

    // DETECCIÓN DE CONTEXTO: ¿Es un bar registrándose o un cliente colando una canción?
    if (params.get('type') === 'song') {
        this.paymentType = 'song';
        this.selectedPlanId = 'SONG';
    }

    // REQUISITO: Cargar precios reales desde el servidor
    this.loadPlansFromDb();
  }

  /**
   * Obtiene la lista de planes y precios guardados en la BD del servidor.
   * Esto cumple con el requisito de no tener precios "hardcodeados".
   */
  loadPlansFromDb() {
    this.http.get<SubscriptionPlan[]>('http://localhost:8080/payments/plans').subscribe({
        next: (plans) => {
            this.availablePlans = plans;
            // Si el usuario viene de pedir una canción, iniciamos el proceso automáticamente
            if (this.paymentType === 'song') {
                this.prepay();
            }
        },
        error: (err) => {
            this.cardError = "Error crítico: No se pudieron obtener los precios del servidor.";
        }
    });
  }

  /** Limpieza de recursos al salir */
  ngOnDestroy(): void {
    if (this.card) {
      try { this.card.unmount(); } catch {}
    }
  }

  /** Paso 1: PREPAY - Solicitar al backend que cree la intención de pago en Stripe */
  prepay(): void {
    if (this.loading || this.processing) return;

    this.loading = true;
    this.cardError = '';
    this.succeeded = false;
    
    // Enviamos el ID del plan. El backend buscará el precio asociado en su base de datos.
    const payload = { 
        planId: this.selectedPlanId, 
        email: this.userEmail || 'usuario_anonimo@gramola.com' 
    };

    this.http.post<StripeTransaction>('http://localhost:8080/payments/prepay', payload).subscribe({ 
      next: (resp) => {
        this.transactionDetails = resp;
        
        // El backend devuelve los datos de Stripe (PaymentIntent) en formato JSON string
        if (this.transactionDetails && typeof this.transactionDetails.data === 'string') {
            try {
                this.transactionDetails.data = JSON.parse(this.transactionDetails.data);
            } catch (e) {
                console.error("Error al procesar los datos de la pasarela", e);
            }
        }

        // Una vez tenemos el secreto, montamos el Iframe de Stripe Elements
        this.mountCard();
        this.showCardForm = true;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.cardError = "No se pudo preparar la transacción. Comprueba tu conexión.";
      },
    });
  }
    
  /** Inicializa el formulario de tarjeta de Stripe */
  private mountCard(): void {
    if (this.elements) return;

    this.elements = this.stripe.elements();
    
    const style = {
      base: {
        color: '#32325d',
        fontFamily: '"Inter", sans-serif',
        fontSmoothing: 'antialiased',
        fontSize: '16px',
        '::placeholder': { color: '#a0aec0' }
      },
      invalid: {
        color: '#fa755a',
        iconColor: '#fa755a',
      }
    };

    // Crea el elemento de tarjeta y lo inyecta en el div #card-element del HTML
    this.card = this.elements.create('card', { style });
    this.card.mount('#card-element');

    // Validaciones en tiempo real (ej: número de tarjeta incompleto)
    this.card.on('change', (event: any) => {
      this.cardError = event.error ? event.error.message : '';
    });
  }

  /** Paso 2: Ejecutar el cobro real a través de Stripe */
  payWithCard(): void {
    if (!this.transactionDetails || this.processing) return;

    this.processing = true;
    this.cardError = '';

    const clientSecret = this.transactionDetails.data?.client_secret;
    
    if (!clientSecret) {
      this.processing = false;
      this.cardError = 'Error interno de la pasarela (Falta client_secret).';
      return;
    }

    // Inicia el flujo de cobro real
    this.stripe.confirmCardPayment(clientSecret, {
      payment_method: { card: this.card }
    })
    .then((res: any) => {
      if (res.error) {
        // Fallo en la tarjeta (ej: declinada)
        this.processing = false;
        this.cardError = res.error.message;
      } else {
        if (res.paymentIntent.status === 'succeeded') {
            // Si Stripe confirma el cobro, avisamos a nuestro backend para que registre la operación
            this.confirmBackend(this.transactionDetails!.id);
        }
      }
    })
    .catch((err: any) => {
      this.processing = false;
      this.cardError = "Error inesperado durante el pago.";
    });
  }

  /** Paso 3: Confirmación final en el servidor y redirección según el flujo */
  private confirmBackend(transactionId: string) {
      // Usamos el token del email (registro) o el email de la sesión activa
      const userIdentifier = this.token ?? this.userEmail;

      const payload = {
          token: userIdentifier, 
          transactionId: transactionId
      };

      this.http.post<any>('http://localhost:8080/payments/confirm', payload).subscribe({
          next: () => {
            this.processing = false;
            this.succeeded = true;

            // REDIRECCIÓN SEGÚN CONTEXTO
            if (this.paymentType === 'song') {
                // Pago de canción: Volver a la gramola para insertar en cola
                setTimeout(() => {
                    this.router.navigate(['/gramola'], { queryParams: { paymentSuccess: 'true' } }); 
                }, 1500);
            } else {
                // Pago de suscripción (Alta): Ir al login para empezar sesión
                setTimeout(() => this.router.navigate(['/login']), 1500);
            }
          },
          error: (err) => {
            this.processing = false;
            this.cardError = "El pago se cobró, pero hubo un error al actualizar tu cuenta en nuestra base de datos.";
          }
      });
  }
  private fmtError(e: any): string {
    if (!e) return 'Error desconocido';
    if (typeof e === 'string') return e;
    if (e.error?.message) return e.error.message;
    if (e.message) return e.message;
    try { return JSON.stringify(e); } catch { return 'Error'; }
  }
}