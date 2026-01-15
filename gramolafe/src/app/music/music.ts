import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Spoty } from '../spoty'; 
import { Router, RouterLink } from '@angular/router'; 
import { HttpClientModule } from '@angular/common/http'; 


/** * INTERFACES DE MODELO
 * Definen la estructura de los datos que recibimos de la API de Spotify
 * para garantizar un tipado seguro en todo el componente.
 */
interface Device {
    id: string;
    name: string;
    type: string;
    is_active: boolean;
}

interface PlayList {
    id: string;
    name: string;
    images: { url: string }[]; 
    tracks: { total: number }; 
    uri: string; 
}

interface NowPlaying {
    name: string;
    artist: string;
    image: string;
    isPlaying: boolean;
    contextName?: string; 
}

/**
 * COMPONENTE ADMINISTRATIVO: PANEL DE GESTIÓN.
 * * Este componente es la herramienta principal del dueño del bar.
 * Permite configurar qué dispositivo sonará, qué música de fondo poner y
 * monitorizar en todo momento lo que los clientes están escuchando.
*/
@Component({
    selector: 'app-music',
    standalone: true,
    imports: [CommonModule, FormsModule, HttpClientModule, RouterLink], 
    templateUrl: './music.html',
    styleUrls: ['./music.css']
})
export class MusicComponent implements OnInit {

    // --- DATOS DE SESIÓN Y VINCULACIÓN ---
    token: string | null = null;
    userEmail: string | null = null; 
    
    // --- ESTADO DE GESTIÓN MUSICAL ---
    /** Lista de dispositivos (PCs, Móviles, Echo) detectados en la cuenta de Spotify */
    devices: Device[] = [];
    /** Dispositivo seleccionado actualmente para la salida de audio */
    currentDevice: Device | null = null;
    /** Colección de listas de reproducción del propietario */
    playlists: PlayList[] = [];
    /** Información en tiempo real de la canción activa */
    nowPlaying: NowPlaying | null = null;

    errorMsg: string | null = null;

    constructor(
        private spotyService: Spoty, 
        private router: Router
    ) {}

     /**
     * INICIALIZACIÓN DEL PANEL.
     * Recupera las credenciales del almacenamiento persistente y arranca
     * la carga de datos del local.
     */
    ngOnInit(): void {
        // PERSISTENCIA: Recuperamos el token y email del localStorage
        // Esto permite que el dueño no tenga que loguearse si refresca la página.
        this.token = localStorage.getItem('spotyToken');
        this.userEmail = localStorage.getItem('userEmail'); 
        

        if (!this.token) {
            this.errorMsg = "Sesión no autorizada.";
            this.router.navigate(['/login']);
            return;
        }

        // Cargar datos iniciales de gestión
        this.loadInitialData();
        
         /**
         * MONITORIZACIÓN EN TIEMPO REAL:
         * Consultamos a Spotify cada 5 segundos para actualizar la tarjeta "Sonando ahora".
         * Esto permite ver qué canción han añadido los clientes de forma inmediata.
         */
        setInterval(() => this.getPlaybackState(), 5000); 
    }
    
    /**
     * NAVEGACIÓN: ABRIR GRAMOLA.
     * Redirige a la vista pública (Kiosco) que se mostrará a los clientes del bar.
     */
    goToGramola(): void {
        this.router.navigate(['/gramola']);
    }

    /** Realiza la carga de los recursos necesarios de Spotify */
    private loadInitialData(): void {
        this.getDevices();
        this.getPlaylists(); 
        this.getPlaybackState(); 
    }
    
    // --- MÉTODOS DE GESTIÓN DE DISPOSITIVOS ---

    /** Recupera los reproductores activos vinculados a la cuenta del bar */
    getDevices(): void {
        if (!this.token) return;

        this.spotyService.getDevices(this.token).subscribe({
            next: (result: any) => {
                this.devices = result.devices;
                // Priorizamos el dispositivo que ya esté sonando (is_active)
                this.currentDevice = this.devices.find(d => d.is_active) || this.devices[0] || null;
            },
            error: (err) => console.error("Error dispositivos:", err)
        });
    }

    /** Permite al dueño cambiar manualmente la salida de audio */
    selectDevice(device: Device): void {
        this.currentDevice = device;
        this.errorMsg = null;
        this.getPlaybackState();
    }

    // --- MÉTODOS DE GESTIÓN DE PLAYLISTS ---

    /** Obtiene el catálogo de listas del dueño para servir como música de fondo */
    getPlaylists(): void {
        if (this.token) {
            this.spotyService.getPlaylists(this.token).subscribe({
                next: (result: any) => {
                    this.playlists = result.items;
                },
                error: (e) => {
                    console.error("Error al cargar playlists:", e);
                }
            });
        }
    }
    
    /** * ACTIVA UNA PLAYLIST.
     * Envía a Spotify la orden de reproducir una lista completa en el dispositivo elegido.
    */
    playPlaylist(playlist: PlayList): void {
        if (!this.currentDevice) {
            alert("Selecciona un dispositivo primero (arriba).");
            return;
        }
        if (!this.token) return;

        if(confirm(`¿Poner "${playlist.name}" como música de fondo?`)) {
            this.spotyService.playContext(this.token, playlist.uri, this.currentDevice.id).subscribe({
                next: () => {
                    // Refrescos rápidos para actualizar la UI inmediatamente tras la orden
                    setTimeout(() => this.getPlaybackState(), 500);
                    setTimeout(() => this.getPlaybackState(), 2000);
                },
                error: (err: any) => {
                    // Gestión de errores de suscripción (Spotify requiere Premium para control remoto)
                    if (err.status === 403) alert("⚠️ Error: Necesitas Spotify Premium.");
                    else console.error(err);
                }
            });
        }
    }

    // --- MONITORIZACIÓN DE REPRODUCCIÓN ---

    /** * CONSULTA EL ESTADO ACTUAL.
     * Sincroniza la información de la canción, artista y carátula.
     * Esta información es la que alimenta el ecualizador animado del HTML.
     */
    getPlaybackState(): void {
        if (!this.token) return;
        this.spotyService.getCurrentPlaybackState(this.token).subscribe({
            next: (data: any) => {
                if (data && data.item) {
                    this.nowPlaying = {
                        name: data.item.name,
                        artist: data.item.artists.map((a: any) => a.name).join(', '),
                        image: (data.item.album && data.item.album.images.length > 0) ? data.item.album.images[0].url : '',
                        isPlaying: data.is_playing,
                        contextName: data.context ? 'Playlist Activa' : undefined 
                    };
                } else {
                    this.nowPlaying = null;
                }
            },
            error: (e) => console.error("Error playback:", e)
        });
    }
}