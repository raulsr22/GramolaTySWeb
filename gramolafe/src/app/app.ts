import { Component, OnInit, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * COMPONENTE RAÍZ DE LA APLICACIÓN (APP COMPONENT).
 * * Este es el punto de entrada.
 * Se encarga de la configuración global y de gestionar la redirección técnica
 * necesaria para asegurar la consistencia del almacenamiento persistente.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrls: ['./app.css'],
})
export class App implements OnInit {
  protected readonly title = signal('gramolafe');

  ngOnInit(): void {
    /**
     * SOLUCIÓN DE PERSISTENCIA Y COMPATIBILIDAD OAUTH
     * * Como usamos localStorage para que los datos se conserven siempre,
     * es obligatorio que el origen (dominio + puerto) sea idéntico al configurado en Spotify.
     * * Si el usuario entra por 'localhost' pero el callback de Spotify redirige a '127.0.0.1',
     * el localStorage parecería estar vacío. Forzamos 127.0.0.1 para unificar el almacenamiento.
     */
    if (window.location.hostname === 'localhost') {
      console.warn('Detectado localhost. Redirigiendo a 127.0.0.1 para compatibilidad con OAuth de Spotify...');
      
      // Construimos la nueva URL reemplazando el nombre del host
      const newUrl = window.location.href.replace('localhost', '127.0.0.1');
      
      // Ejecutamos la redirección inmediata
      window.location.replace(newUrl);
    }
  }
}