import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router'; 
import { User } from '../user';

/**
 * CONTROLADOR PARA LA SOLICITUD DE RECUPERACIÓN DE CONTRASEÑA.
 * * Este componente gestiona el primer paso del flujo de "olvido de contraseña".
 * Permite al usuario introducir su email para recibir un enlace de restauración
 * generado por el backend.
*/
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrls: ['./forgot-password.css'],
})
export class ForgotPasswordComponent {
  /** Correo electrónico vinculado al formulario mediante ngModel */
  email = '';

  /** Estado de carga para mostrar el spinner y deshabilitar el botón */
  loading = false;

  /** Mensaje de éxito o feedback positivo */
  msg = '';

  /** Mensaje de error para fallos de validación o red */
  err = '';

  constructor(private userService: User) {}

  /**
   * INICIA EL PROCESO DE RESETEO.
   * * Envía el correo electrónico al backend para que este genere un token 
   * y envíe el email real mediante el EmailService de Spring Boot.
  */
  sendResetLink(): void {
    // Reset de estados previos
    this.msg = '';
    this.err = '';

    // Validación básica de entrada
    if (!this.email) {
      this.err = 'Introduce tu email';
      return;
    }

    this.loading = true;

    // Invocación al servicio de usuario (Backend)
    this.userService.requestPasswordReset(this.email).subscribe({
      next: (resp) => {
        this.loading = false;
        /**
         * ÉXITO: El backend ha procesado la solicitud.
         * Se muestra el mensaje informativo devuelto por el servidor.
        */
        this.msg = resp?.message || 'Se ha enviado un correo con el enlace de recuperación (si el usuario existe).';
      },
      error: (e) => {
        this.loading = false;
        /**
         * SEGURIDAD:
         * Por norma general de seguridad, no confirmamos si el email existe o no 
         * en nuestra base de datos para evitar que atacantes descubran correos registrados.
         * Mostramos un mensaje genérico incluso si el backend devuelve error.
        */
        this.msg = 'Si el usuario existe, se ha enviado un enlace de recuperación.';
        console.error('Error al pedir reset de contraseña', e);
      },
    });
  }
}
