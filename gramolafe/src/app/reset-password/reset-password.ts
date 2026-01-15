import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { User } from '../user';

/**
 * COMPONENTE DE RESTABLECIMIENTO (RESET PASSWORD).
 * * Este controlador gestiona el paso final del flujo de "contraseña olvidada".
 * Se encarga de capturar el token de seguridad y el email del usuario desde 
 * los parámetros de la URL, validar la nueva clave y ejecutar el cambio en el servidor.
*/
@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reset-password.html',
  styleUrls: ['./reset-password.css'],
})
export class ResetPasswordComponent implements OnInit {
  // Datos de identificación recuperados de la URL
  email = '';
  token = '';

  // Datos del formulario
  newPwd = '';
  repeatPwd = '';

  // Estados de control de la vista
  loading = false;
  msg = '';
  err = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private userService: User
  ) {}

  /**
   * INICIALIZACIÓN DEL COMPONENTE.
   * * Recupera el 'email' y el 'token' enviados por el backend en el enlace del correo.
   * Si faltan estos datos, el proceso no puede continuar por seguridad.
  */
  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.email = params.get('email') ?? '';
    this.token = params.get('token') ?? '';

    if (!this.email || !this.token) {
      this.err = 'Faltan parámetros en el enlace de recuperación.';
    }
  }

  /**
   * EJECUTA EL CAMBIO DE CONTRASEÑA.
   * * Realiza validaciones en el lado del cliente (coincidencia y longitud)
   * antes de enviar la petición al servicio de backend.
   */
  changePassword(): void {
    this.msg = '';
    this.err = '';

    // 1. Verificación de seguridad de los parámetros técnicos
    if (!this.email || !this.token) {
      this.err = 'El enlace no es válido.';
      return;
    }

    // 2. Validación de nueva contraseña
    if (!this.newPwd || !this.repeatPwd) {
      this.err = 'Rellena las dos contraseñas.';
      return;
    }

    // 3. Validación de complejidad mínima
    if (this.newPwd.length < 8) {
      this.err = 'La contraseña debe tener al menos 8 caracteres.';
      return;
    }

    // 4. Validación de coincidencia
    if (this.newPwd !== this.repeatPwd) {
      this.err = 'Las contraseñas no coinciden.';
      return;
    }

    this.loading = true;

    // 5. Invocación al Backend para actualizar el Hash en la base de datos
    this.userService
      .resetPassword(this.email, this.token, this.newPwd)
      .subscribe({
        next: (resp) => {
          this.loading = false;
          /**
           * ÉXITO: Contraseña actualizada.
           * Se muestra un mensaje confirmando que el token ha sido consumido
           * y se redirige automáticamente al login tras una breve pausa.
          */
          this.msg =
            resp?.message || 'Contraseña actualizada correctamente.';
          setTimeout(() => this.router.navigate(['/login']), 2500);
        },
        error: (e) => {
          this.loading = false;
          /**
           * ERROR: El token podría haber caducado (30 min) o ser inválido.
          */
          this.err =
            e?.error?.message ||
            e?.message ||
            'No se ha podido actualizar la contraseña.';
          console.error('Error en resetPassword', e);
        },
      });
  }
}
