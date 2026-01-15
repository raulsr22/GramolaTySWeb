import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { User } from '../user';
import { RouterLink, Router } from '@angular/router'; 
import { Spoty } from '../spoty'; 

/**
 * COMPONENTE DE CONTROL DE ACCESO (LOGIN).
 * * Gestiona la autenticación de los establecimientos, la persistencia de sesión
 * en el navegador y realiza diagnósticos preventivos de geolocalización para
 * asegurar que el bar cumple con los requisitos técnicos antes de operar.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],  
  templateUrl: './login.html',
  styleUrls: ['./login.css']
})
export class Login {

  email = '';
  pwd = '';

  loading = false;
  msg = '';
  err = '';

  /** * Almacena la imagen de la firma recuperada del backend (Base64).
   * Se utiliza para el bloque de verificación de identidad en el HTML.
*/
  signatureImage: string | null = null;

  constructor(private userService: User, private router: Router, private spotyService: Spoty) {}

  /**
   * PROCESO DE INICIO DE SESIÓN.
   * * Realiza la validación de credenciales, guarda los datos de sesión y
   * ejecuta una rutina de diagnóstico de ubicación para el administrador.
  */
  entrar() {
    this.msg = '';
    this.err = '';
    this.signatureImage = null; // Resetear firma previa

    if (!this.email || !this.pwd) {
      this.err = 'Introduce email y contraseña';
      return;
    }

    this.loading = true;

    this.userService.login(this.email, this.pwd).subscribe({
      next: (res:any) => {
        this.loading = false;
        
        // COMPROBACIÓN DE SEGURIDAD
        if (!res.clientId) {
            this.err = 'Error: Este usuario no tiene Client ID de Spotify registrado. Por favor, crea una cuenta nueva.';
            return;
        }

        this.msg = 'Login correcto. Redirigiendo para autorización de Spotify...';

        // Guardamos el clientId y el email que nos devuelve el backend
        localStorage.setItem('clientId', res.clientId);
        localStorage.setItem('userEmail', this.email);

        // --- LÓGICA DE GEOLOCALIZACIÓN Y DIAGNÓSTICO ---
        if (res.lat && res.lng) {
            const barLat = parseFloat(res.lat);
            const barLng = parseFloat(res.lng);
            
            localStorage.setItem('barLat', barLat.toString());
            localStorage.setItem('barLng', barLng.toString());

            console.group('📍 DIAGNÓSTICO DE UBICACIÓN');
            console.log(`🏢 Coordenadas del Bar (Base de Datos): ${barLat}, ${barLng}`);

            // Intentamos obtener la ubicación actual del navegador para comparar
            if (navigator.geolocation) {
                navigator.geolocation.getCurrentPosition((pos) => {
                    const myLat = pos.coords.latitude;
                    const myLng = pos.coords.longitude;
                    const distKm = this.getDistanceFromLatLonInKm(barLat, barLng, myLat, myLng);
                    const distMetros = (distKm * 1000).toFixed(0);

                    console.log(`👤 Tu Ubicación (PC/Móvil): ${myLat}, ${myLng}`);
                    console.log(`📏 Distancia calculada: ${distMetros} metros`);

                    if (distKm > 0.2) { // 200 metros de margen
                        console.warn(`⚠️ ESTÁS LEJOS: A ${distMetros}m. La app requiere estar a menos de 200m.`);
                        console.warn('💡 SOLUCIÓN EN PC: Abre DevTools (F12) -> Ctrl+Shift+P -> "Sensors" -> Pon las coordenadas del bar manualmente.');
                    } else {
                        console.log('✅ ESTÁS DENTRO DEL RANGO. Todo debería funcionar.');
                    }
                    console.groupEnd();
                }, (error) => {
                    console.error('❌ No se pudo obtener tu ubicación para comparar:', error);
                    console.groupEnd();
                });
            }
        }else {
            // Limpiar por si acaso es un usuario antiguo sin coordenadas
            localStorage.removeItem('barLat');
            localStorage.removeItem('barLng');
        }

        // --- GESTIÓN DE FIRMA DIGITAL (Requisito Extra) ---
        if (res.signature) {
            localStorage.setItem('barSignature', res.signature);
            this.signatureImage = res.signature;
            this.msg = 'Identidad verificada correctamente.';
            // No redirigimos automáticamente para permitir que el usuario vea su firma antes de ir a Spotify
        } else {
            // Si no hay firma, redirigimos directamente o mostramos mensaje
            this.msg = 'Login correcto. Redirigiendo...';
            this.redirectToSpotify();
        }
      },
      error: (e) => {
        this.loading = false;
        console.error('Error en login', e);

        if (e.error?.message) {
          this.err = e.error.message;
        } else {
          this.err = 'Credenciales incorrectas o error en el servidor';
        }
      }
    });
  }
    
   /**
   * FLUJO OAUTH 2.0: REDIRECCIÓN EXTERNA.
   * * Solicita al servicio de Spotify la URL de autorización y redirige
   * el navegador del usuario a la página oficial de permisos de Spotify.
   */
    redirectToSpotify(): void {
        const clientId = localStorage.getItem('clientId');
        
        if (!clientId) {
            this.err = 'Error crítico: No se pudo guardar el Client ID en la sesión.';
            return;
        }

        // Usamos el método del servicio Spoty para construir la URL
        const authorizationUrl = this.spotyService.buildAuthorizationUrl(clientId); 
        
        // Redirigimos
        window.location.href = authorizationUrl;
    }

  // --- Función auxiliar para calcular distancia entre dos puntos ---
  private getDistanceFromLatLonInKm(lat1: number, lon1: number, lat2: number, lon2: number) {
    const R = 6371; // Radio de la tierra en km
    const dLat = this.deg2rad(lat2 - lat1);
    const dLon = this.deg2rad(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(this.deg2rad(lat1)) * Math.cos(this.deg2rad(lat2)) *
      Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c; // Distancia en km
  }

  /** Convierte grados decimales a radianes */
  private deg2rad(deg: number) {
    return deg * (Math.PI / 180);
  }
}