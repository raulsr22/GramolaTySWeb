import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Spoty } from '../spoty'; 
import { FormsModule } from '@angular/forms';

/**
 * COMPONENTE DE MANEJO DE RETORNO (OAUTH 2.0 CALLBACK).
 * * Este componente es el destino al que Spotify redirige al usuario tras conceder permisos.
 * Su función es puramente lógica y de seguridad: recibe un código temporal y lo valida
 * antes de solicitar el token de acceso definitivo al backend.
*/
@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './callback.html',
  styleUrls: ['./callback.css'] 
})
export class CallbackComponent implements OnInit {
  statusMessage: string = 'Iniciando verificación de Spotify...';
  errorMessage: string | null = null;
  loading: boolean = true;

  constructor(
    private route: ActivatedRoute, 
    private router: Router, 
    private spotyService: Spoty
  ) { }

  ngOnInit(): void {
    // 1. Obtener los parámetros de la URL enviados por Spotify
    const qp = this.route.snapshot.queryParamMap;
    const code = qp.get('code');
    const state = qp.get('state');
    const error = qp.get('error');
    
    // Recuperamos el clientId guardado previamente en el Login para identificar al bar.
    const clientId = localStorage.getItem('clientId');
    
    // Depuración para monitorizar el flujo en la consola del navegador.
    console.log('--- Callback Debug ---');
    console.log('Code recibido:', code);
    console.log('State recibido:', state);
    console.log('ClientId en LocalStorage:', clientId);
    console.log('Error de Spotify:', error);

      // 2. GESTIÓN DE ERRORES EXTERNOS:
    // Si el usuario pulsa "Cancelar" en Spotify o hay un error de red, Spotify devuelve 'error'.
    if (error) {
      this.statusMessage = 'Error de autorización de Spotify.';
      this.errorMessage = `Acceso denegado por el usuario o Spotify: ${error}.`;
      this.loading = false;
      return;
    }
    
    // 3. VALIDACIÓN DE INTEGRIDAD:
    // Comprobamos que tenemos todos los datos necesarios para el intercambio del token.
    if (!code || !state || !clientId) {
      this.statusMessage = 'Fallo en la comunicación.';
      
      // Construimos un mensaje más específico para saber qué falla
      const missing = [];
      if (!code) missing.push('code');
      if (!state) missing.push('state');
      if (!clientId) missing.push('Client ID (no se guardó en el login)');

      this.errorMessage = `Faltan parámetros críticos: ${missing.join(', ')}.`;
      this.loading = false;
      return;
    }

    // 4. VALIDACIÓN DE SEGURIDAD:
    // El 'state' recibido debe coincidir exactamente con el que generamos al iniciar el login.
    // Esto garantiza que la respuesta que estamos procesando proviene de una solicitud nuestra.
    const expectedState = localStorage.getItem('oauth_state');
    
    // Se consume el state inmediatamente por seguridad (un solo uso).
    localStorage.removeItem('oauth_state'); 
    
    if (state !== expectedState) {
        console.error(`State mismatch! Recibido: ${state}, Esperado: ${expectedState}`);
        this.statusMessage = 'Error de seguridad.';
        this.errorMessage = 'El estado OAuth recibido no coincide con el esperado (posible CSRF).';
        this.loading = false;
        return;
    }

        // 5. FLUJO DE INTERCAMBIO:
    // Una vez validados nosotros, enviamos el 'code' a nuestro backend.
    // Es el backend quien realmente contactará con Spotify usando el 'Client Secret'.
    this.statusMessage = 'Código recibido. Solicitando Access Token al servidor...';
    
    this.spotyService.getAuthorizationToken(code, clientId).subscribe({
      next: (data) => {
        // ÉXITO: Guardamos el Access Token resultante en el almacenamiento local.
        console.log('Token recibido:', data);
        localStorage.setItem('spotyToken', data.access_token);
        
        this.statusMessage = '¡Autorización completa! Redirigiendo a la Gramola.';
        this.loading = false;
        
        // Redirección automática al panel de gestión del bar.
        this.router.navigateByUrl('/music'); 
      },
      error: (err) => {
        // ERROR: El backend ha fallado al intercambiar el token (ej: código expirado).
        this.loading = false;
        this.statusMessage = 'Fallo al obtener Access Token.';
        this.errorMessage = err.error?.message || 'Error en el backend al solicitar el token.';
        console.error('Error fetching access token:', err);
      }
    });
  }
}