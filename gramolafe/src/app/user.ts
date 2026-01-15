import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * SERVICIO DE GESTIÓN DE USUARIOS.
 * * Este servicio actúa como el enlace entre el frontend y el backend
 * para todas las operaciones de identidad, acceso y seguridad de los establecimientos.
*/
@Injectable({
  providedIn: 'root'
})
export class User {
  private baseUrl = 'http://localhost:8080/users';

  constructor(private http: HttpClient) {}

  /**
   * REGISTRO DE ESTABLECIMIENTO.
   * * Envía todos los datos necesarios para dar de alta un bar, incluyendo
   * los parámetros de Spotify y los requisitos adicionales.
   * * @param bar Nombre del local.
   * @param email Correo electrónico único.
   * @param pwd Contraseña de acceso.
   * @param clientId ID de aplicación Spotify.
   * @param clientSecret Clave secreta Spotify.
   * @param address Dirección física (usada por el backend para Geocoding).
   * @param signature Imagen de la firma digital en formato Base64.
  */
  register(
    bar: string, 
    email: string, 
    pwd: string, 
    clientId: string, 
    clientSecret: string,
    address: string, signature: string
  ): Observable<void> {
    
    /**
     * Construcción del objeto de información. 
     * Enviamos 'pwd1' y 'pwd2' con el mismo valor para satisfacer la validación 
     * de coincidencia del controlador del backend sin requerir lógica extra en el servidor.
    */
    const info = { bar, email, pwd1: pwd, pwd2: pwd, clientId, clientSecret, address, signature }; 
	    
    // El backend espera un POST con los 6 campos originales del formulario, usando pwd como pwd1/pwd2.
    // Para simplificar el UserService del backend, enviamos pwd como pwd1 y pwd2 (ya que sabemos que son iguales).
    return this.http.post<void>(`${this.baseUrl}/register`, info);
  }
  
  /**
   * INICIO DE SESIÓN (LOGIN).
   * * Valida las credenciales del bar. 
   * @returns Un observable con la respuesta del servidor que incluye las 
   * coordenadas geográficas (lat/lng) para el control de distancia de 100m.
  */
  login(email: string, pwd: string) {
    const body = { email, pwd };
    // Ahora esperamos lat y lng en la respuesta
    return this.http.post<{ email: string; clientId: string; lat?: number; lng?: number; signature?: string }>(
      `${this.baseUrl}/login`,
      body
    );
  }

  /**
   * SOLICITUD DE RECUPERACIÓN DE CONTRASEÑA.
   * * Inicia el flujo de "olvido de contraseña" solicitando al servidor 
   * la generación de un token y el envío del email correspondiente.
  */
  requestPasswordReset(email: string): Observable<{
    status: string;
    message: string;
    resetUrl?: string;
  }> {
    const body = { email };
    return this.http.post<{
      status: string;
      message: string;
      resetUrl?: string;
    }>(`${this.baseUrl}/password/token`, body);
  }

  /**
   * CAMBIO DE CONTRASEÑA (RESET).
   * * Ejecuta la actualización final de la clave utilizando el token de 
   * seguridad validado previamente.
   * * @param email Correo de la cuenta.
   * @param token Identificador único de reset.
   * @param newPwd Nueva clave segura elegida por el usuario.
  */
  resetPassword(
    email: string,
    token: string,
    newPwd: string
  ): Observable<{ status: string; message: string }> {
    const body = { email, token, newPwd };
    return this.http.post<{ status: string; message: string }>(
      `${this.baseUrl}/password/reset`,
      body
    );
  }
}