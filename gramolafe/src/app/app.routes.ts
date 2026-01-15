import { Routes } from '@angular/router';
import { Register } from './register/register';
import { PaymentComponent } from './payments/payments';
import { Login } from './login/login';
import { CallbackComponent } from './callback/callback'; // Importamos el componente de callback
import { MusicComponent } from './music/music'; // 🚨 CRÍTICO: Importamos el nuevo MusicComponent
import { GramolaComponent} from './gramola/gramola';
import { ForgotPasswordComponent } from './forgot-password/forgot-password';
import { ResetPasswordComponent } from './reset-password/reset-password';

import { HomeComponent } from './home/home';

export const routes: Routes = [
    
  /** * RUTA RAÍZ (LANDING PAGE).
     * Punto de entrada público que presenta el producto "La Gramola".
  */
  { path: '', component: HomeComponent },
   
  /** * FLUJO DE GESTIÓN DE CUENTAS.
     * Rutas encargadas del alta, autenticación y pago de establecimientos.
   */
  { path: 'register', component: Register },
  { path: 'payments', component: PaymentComponent },
  { path: 'login', component: Login },

   /** * INTEGRACIÓN TÉCNICA: SPOTIFY OAUTH 2.0.
   * RUTA CRÍTICA: Es el destino del redireccionamiento de Spotify.
   * Procesa el código de autorización para convertirlo en un Token de acceso.
   */
  { path: 'callback', component: CallbackComponent }, 

  /** * VISTAS OPERATIVAS PRINCIPALES.
  * 1. 'music': El panel administrativo privado para el dueño del bar.
  * 2. 'gramola': La interfaz pública para que los clientes elijan canciones.
  */
  { path: 'music', component: MusicComponent }, 
  { path: 'gramola', component: GramolaComponent },

  /** * RECUPERACIÓN DE ACCESO.
  * Permite solicitar y ejecutar el cambio de clave mediante tokens de email.
  */
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: 'reset-password', component: ResetPasswordComponent },


  /** * GESTIÓN DE ERRORES.
  * Cualquier URL no definida anteriormente redirige automáticamente al registro.
  * Asegura que el usuario no se quede en una página en blanco.
  */
  { path: '**', redirectTo: 'register' },
];