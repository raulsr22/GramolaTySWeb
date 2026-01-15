import { Component,ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { User } from '../user';
import { Router } from '@angular/router'; 

/**
 * COMPONENTE DE REGISTRO DE ESTABLECIMIENTOS.
 * * Este controlador gestiona el alta de nuevos bares, integrando la captura
 * de datos del negocio, credenciales técnicas de Spotify y una firma digital
 * obligatoria para completar el requisito extra.
*/
@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './register.html',
  styleUrls: ['./register.css'] 
})
export class Register {
  
  // --- PROPIEDADES DE VINCULACIÓN (NgModel) ---
  /** Nombre comercial del establecimiento */
  bar = '';
  /** Correo oficial de contacto y login */
  email = '';
  /** Dirección física para geolocalización automática en el backend */
  address = '';
  /** Credenciales de la API de Spotify del bar */
  clientId = '';
  clientSecret = '';
  /** Gestión de contraseñas de acceso */
  pwd1 = '';
  pwd2 = '';

  // --- ESTADOS DE LA INTERFAZ ---
  loading = false;
  msg = '';
  err = '';

  // --- LÓGICA DEL PANEL DE FIRMA (HTML5 CANVAS) ---
  /** Referencia directa al elemento <canvas> definido en la plantilla HTML */
  @ViewChild('signatureCanvas') canvasRef!: ElementRef<HTMLCanvasElement>;
  /** Contexto de dibujo 2D del Canvas */
  private ctx!: CanvasRenderingContext2D;
  /** Estado que indica si el usuario está trazando actualmente */
  private isDrawing = false;
  /** Flag de validación para asegurar que el campo de firma no está vacío */
  hasSigned = false;  

  constructor(private service: User, private router: Router) {} 

  /**
   * INICIALIZACIÓN DEL ENTORNO DE DIBUJO.
   * Se ejecuta una vez que Angular ha renderizado la vista y el Canvas es accesible.
  */
  ngAfterViewInit() {
    if (this.canvasRef) {
      const canvas = this.canvasRef.nativeElement;
      this.ctx = canvas.getContext('2d')!;

      // Configuración del trazo
      this.ctx.lineWidth = 2.5;
      this.ctx.lineCap = 'round';
      this.ctx.lineJoin = 'round';
      this.ctx.strokeStyle = '#1e293b'; // Color tinta oscura

      this.resizeCanvas();
      // Mantener el canvas ajustado
      window.addEventListener('resize', this.resizeCanvas.bind(this));
    }
  }

  /**
   * AJUSTE DINÁMICO DEL CANVAS.
   * Sincroniza la resolución interna del dibujo con el tamaño visual del CSS 
   * para evitar distorsiones en el trazo.
  */
  resizeCanvas() {
    if (!this.canvasRef) return;
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    
    // Solo ajustar si cambia el tamaño visual para evitar borrar el dibujo innecesariamente
    if (canvas.width !== rect.width || canvas.height !== rect.height) {
      canvas.width = rect.width;
      canvas.height = rect.height;
      // La configuración del contexto se resetea al cambiar el ancho/alto
      if (this.ctx) {
        this.ctx.lineWidth = 2.5;
        this.ctx.lineCap = 'round';
        this.ctx.lineJoin = 'round';
        this.ctx.strokeStyle = '#1e293b';
      }
    }
  }

  // --- MÉTODOS DE INTERACCIÓN TÁCTIL/RATÓN ---

  /** Calcula la posición exacta del cursor o dedo relativa al borde del Canvas */
  private getPosition(event: MouseEvent | TouchEvent): { x: number, y: number } {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    let clientX, clientY;

    if (event instanceof MouseEvent) {
      clientX = event.clientX;
      clientY = event.clientY;
    } else {
      clientX = event.touches[0].clientX;
      clientY = event.touches[0].clientY;
    }
    return { x: clientX - rect.left, y: clientY - rect.top };
  }

  /** Inicia el trazo al presionar sobre el área de firma */
  startDrawing(event: MouseEvent | TouchEvent) {
    if (event.cancelable) event.preventDefault(); // Evitar scroll en móvil
    this.isDrawing = true;
    this.hasSigned = true;
    this.err = ''; // Limpiar errores al interactuar
    
    const pos = this.getPosition(event);
    this.ctx.beginPath();
    this.ctx.moveTo(pos.x, pos.y);
  }

  /** Dibuja el camino siguiendo el movimiento del puntero */
  draw(event: MouseEvent | TouchEvent) {
    if (!this.isDrawing) return;
    if (event.cancelable) event.preventDefault();
    
    const pos = this.getPosition(event);
    this.ctx.lineTo(pos.x, pos.y);
    this.ctx.stroke();
  }

  /** Finaliza el trazo al levantar el puntero */
  stopDrawing() {
    if (this.isDrawing) {
      this.ctx.closePath();
      this.isDrawing = false;
    }
  }

  /** Limpia completamente el lienzo para repetir la firma */
  clearCanvas() {
    const canvas = this.canvasRef.nativeElement;
    this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    this.hasSigned = false;
  }

  // --- LÓGICA DE ENVÍO ---

  /**
   * PROCESO DE REGISTRO.
   * * Valida los datos obligatorios, convierte la firma de imagen a texto (Base64)
   * e invoca al servicio de backend para procesar el alta.
  */
  registrar(): void {
    this.msg = '';
    this.err = '';

    // 1. Validaciones de Integridad
    if (!this.bar || !this.email || !this.clientId || !this.clientSecret || !this.pwd1 || !this.pwd2) {
      this.err = 'Rellena todos los campos, incluyendo los de Spotify.';
      return;
    }

    if (!this.hasSigned) {
      this.err = 'Es obligatorio firmar para registrarse.';
      return;
    }

    if (this.pwd1 !== this.pwd2) {
      this.err = 'Las contraseñas no coinciden';
      return;
    }

    this.loading = true;

    // 2. EXTRACCIÓN DE LA FIRMA DIGITAL:
    // Convertimos el dibujo del Canvas en una cadena Base64 (PNG) para persistencia en BD.
    let signatureBase64 = '';
    if (this.canvasRef && this.canvasRef.nativeElement) {
      signatureBase64 = this.canvasRef.nativeElement.toDataURL('image/png');
    }
    
    // 3. LLAMADA AL SERVICIO (BACKEND):
    // Se envían los datos incluyendo la dirección (que el backend usará para Geocoding).
    this.service.register(
      this.bar, 
      this.email, 
      this.pwd1, 
      this.clientId, 
      this.clientSecret, 
      this.address, signatureBase64
    ).subscribe({
      next: () => {
        this.loading = false;
        /**
         * ÉXITO: El usuario ha sido registrado.
         * Se informa de que debe revisar su email para activar la cuenta 
         * y proceder al pago de la suscripción.
         */
        this.msg = 'Registro completado. Revisa tu correo electrónico para confirmar la cuenta y realizar el pago.';
      },
      error: (e) => {
        this.loading = false;
        this.err = this.fmtError(e);
        console.error('Error en el registro', e);
      }
    });
  }

  /** Utilidad para formatear los errores devueltos por el servidor */
  private fmtError(e: any): string {
    // Manejo de errores simplificado
    if (!e) return 'Error desconocido';
    if (e.error?.message) return e.error.message;
    if (e.message) return e.message;
	try { return JSON.stringify(e); } catch { return 'Error'; }
  }
}