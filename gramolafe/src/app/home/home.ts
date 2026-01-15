import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

/**
 * CONTROLADOR DE LA PÁGINA DE PORTADA (BIENVENIDA).
 * * Este componente actúa como la cara pública de "La Gramola". Al ser una página 
 * predominantemente informativa y de navegación, se ha diseñado para ser 
 * extremadamente ligero, delegando todo el control del flujo al Router de Angular.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent {
    /**
   * No se requiere lógica compleja ni propiedades de estado en este componente,
   * ya que la interacción del usuario se basa exclusivamente en la navegación 
   * hacia los módulos de Registro o Login mediante directivas [routerLink] 
   * definidas en la plantilla HTML.
   */
}