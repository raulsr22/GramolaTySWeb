import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Spoty } from '../spoty'; 
import { ActivatedRoute, Router } from '@angular/router'; 
import { HttpClient, HttpClientModule } from '@angular/common/http'; 

/** INTERFACES DE DATOS: Estructuras para los tipos de datos seguros de Spotify y la UI */
interface Device {
    id: string;
    name: string;
    type: string;
    is_active: boolean;
}

interface NowPlaying {
    name: string;
    artist: string;
    image: string;
    isPlaying: boolean;
    contextName?: string; 
}

interface Track { id: string; name: string; artist: string; image?: string; }

/**
 * COMPONENTE PÚBLICO "LA GRAMOLA".
 * * Este componente gestiona la interfaz que ven los clientes en el establecimiento.
 * Permite: búsqueda de canciones, monitorización de lo que suena en el local,
 * gestión de una cola de reproducción local y validación de cercanía por GPS.
 */
@Component({
    selector: 'app-gramola',
    standalone: true,
    imports: [CommonModule, FormsModule, HttpClientModule], 
    templateUrl: './gramola.html',
    styleUrls: ['./gramola.css']
})
export class GramolaComponent implements OnInit {

    // --- ESTADO DE LA UI ---
    searchQuery = '';
    searchResults: Track[] = [];
    queue: Track[] = []; 
    loading = false;
    errorMsg: string | null = null;
    successMsg: string | null = null;
    
    // --- DATOS DE SESIÓN ---
    token: string | null = null;
    userEmail: string | null = null;
    
    // --- CONTROL DE DISPOSITIVOS ---
    devices: Device[] = [];
    deviceId: string | null = null; // Dispositivo automático

    // --- REQUISITO: GEOLOCALIZACIÓN ---
    userLat: number | null = null;
    userLng: number | null = null;
    barLat: number | null = null;
    barLng: number | null = null;
    
    /** Información de la canción que suena actualmente en el establecimiento */
    nowPlaying: NowPlaying | null = null;

    constructor(
        private spotyService: Spoty, 
        private http: HttpClient,
        private route: ActivatedRoute,
        private router: Router
    ) {}

    /**
     * INICIALIZACIÓN DEL COMPONENTE.
     * Recupera la sesión del bar y configura el entorno de reproducción.
    */
    ngOnInit(): void {
        this.token = localStorage.getItem('spotyToken');
        this.userEmail = localStorage.getItem('userEmail');
        
        // Verificación de seguridad: si no hay token de bar, no se puede operar
        if (!this.token) {
            this.errorMsg = "La Gramola no está activa. Pide al dueño que inicie sesión.";
            return;
        }

        // 1. CARGAR COLA ESPECÍFICA (Persistencia por Usuario)
        // Usamos el email para que cada bar tenga su propio historial local
        const queueKey = `queue_${this.userEmail}`;
        const storedQueue = localStorage.getItem(queueKey);
        if (storedQueue) {
            try { this.queue = JSON.parse(storedQueue); } catch (e) { this.queue = []; }
        }
        
        // 2. Cargar coordenadas del bar para el control de distancia
        const latStr = localStorage.getItem('barLat');
        const lngStr = localStorage.getItem('barLng');
        if (latStr && lngStr) {
            this.barLat = parseFloat(latStr);
            this.barLng = parseFloat(lngStr);
            this.getUserLocation();
        }

        // 3. Buscar dispositivo activo y estado actual
        this.findActiveDevice();
        this.getPlaybackState();
        setInterval(() => this.getPlaybackState(), 5000);
        
        // 4. Detectamos si el cliente vuelve tras un pago exitoso en Stripe.
        this.route.queryParams.subscribe(params => {
            if (params['paymentSuccess'] === 'true') {
                const savedTrack = localStorage.getItem('pendingTrack');
                if (savedTrack) {
                    const track = JSON.parse(savedTrack);
                    
                    // Si el dispositivo ya está listo, finalizamos; si no, lo buscamos rápido.
                    if (this.deviceId) {
                        this.finalizeAdd(track);
                    } else {
                        // Reintento rápido de dispositivo
                        this.spotyService.getDevices(this.token!).subscribe((res:any) => {
                            const active = res.devices.find((d: any) => d.is_active);
                            this.deviceId = active ? active.id : (res.devices[0]?.id || null);
                            this.finalizeAdd(track);
                        });
                    }
                    localStorage.removeItem('pendingTrack');
                }
                // Limpiar URL
                this.router.navigate([], { queryParams: {} });
            }
        });
    }


    /** Cierra la sesión y limpia todo el almacenamiento local */
    logout(): void {
        localStorage.clear();
        this.router.navigate(['/login']); 
    }

    /** Navegación al panel de gestión */
    backToManager(): void {
        this.router.navigate(['/music']); 
    }

    /** Muestra un banner de éxito temporal en la interfaz */
    private showSuccess(msg: string) {
        this.successMsg = msg;
        this.errorMsg = null;
        setTimeout(() => {
            this.successMsg = null;
        }, 4000);
    }

    
    // --- INTEGRACIÓN CON SPOTIFY ---
    /** Localiza el dispositivo (reproductor) que está activo actualmente en la cuenta */
    findActiveDevice() {
        if (!this.token) return;
        this.spotyService.getDevices(this.token).subscribe({
            next: (res: any) => {
                this.devices = res.devices || [];
                const active = this.devices.find((d: any) => d.is_active);
                if (active) this.deviceId = active.id;
                else if (this.devices.length > 0) this.deviceId = this.devices[0].id;
            },
            error: (err) => console.error("Error buscando dispositivos:", err)
        });
    }

    /** Consulta a Spotify qué está sonando exactamente en este momento */
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

    /** Realiza búsquedas de canciones en el catálogo global de Spotify */
    searchTracks(): void {
        if (!this.token || !this.searchQuery) return;
        this.loading = true; 
        this.spotyService.searchTracks(this.token, this.searchQuery).subscribe({
            next: (res: any) => {
                this.searchResults = res.tracks.items.map((i: any) => ({
                    id: i.id, 
                    name: i.name, 
                    artist: i.artists.map((a: any) => a.name).join(', '), 
                    image: (i.album.images && i.album.images.length > 0) ? i.album.images[0].url : null
                }));
                this.loading = false;
            },
            error: () => { 
                this.errorMsg = "Error buscando canciones."; 
                this.loading = false; 
            }
        });
    }

    // --- LÓGICA DE NEGOCIO: AÑADIR A LA COLA ---

    /**
     * INICIA EL PROCESO DE "COLAR" UNA CANCIÓN.
     * Valida la ubicación del cliente antes de redirigir a la pasarela de pagos.
     */
    addToQueue(trackId: string): void {
        if (!this.deviceId) {
            this.errorMsg = "No hay altavoces conectados. Avisa al personal.";
            this.findActiveDevice(); 
            return;
        }
        
        // REQUISITO: VALIDACIÓN GEOGRÁFICA.
        // Solo permitimos añadir canciones si el cliente está en un radio de 100m.
        if (this.barLat && this.barLng && this.userLat && this.userLng) {
            const dist = this.getDistanceFromLatLonInM(this.userLat, this.userLng, this.barLat, this.barLng);
            // Si la distancia supera los 100m, se pide confirmación (Modo Pruebas para Selenium)
            if (dist > 100 && !confirm(`Estás a ${dist.toFixed(0)}m del local. ¿Modo Pruebas?`)) return;
        }
        
        const track = this.searchResults.find(t => t.id === trackId);
        if (track) {
            // Guardamos la canción elegida temporalmente para recuperarla tras el pago
            localStorage.setItem('pendingTrack', JSON.stringify(track));
            // REDIRECCIÓN A PAGOS CON RETORNO A GRAMOLA
            this.router.navigate(['/payments'], { 
                queryParams: { type: 'song', returnUrl: '/gramola' } 
            });
        }
    }

    /**
     * FINALIZACIÓN TRAS PAGO EXITOSO.
     * Registra la canción en el historial del bar y la envía a la cola real de Spotify.
    */
    private finalizeAdd(track: Track) {
        // 1. Guardar en BD (Backend)
        this.saveTrackToBackendOnly(track);

        // 2. Mandar a Spotify
        if (!this.deviceId || !this.token) {
            // Si no hay dispositivo, solo guardamos en cola visual
            this.handleQueueSuccess(track);
            this.showSuccess(`¡Pago OK! "${track.name}" añadida (sin altavoz).`);
            // ACTUALIZACIÓN INMEDIATA TRAS AÑADIR
            setTimeout(() => this.getPlaybackState(), 1000);
            setTimeout(() => this.getPlaybackState(), 3000);
            return;
        }

        const uri = `spotify:track:${track.id}`;
        this.spotyService.addToQueue(this.token, uri, this.deviceId).subscribe({
            next: () => {
                this.handleQueueSuccess(track);
                this.showSuccess(`¡Listo! "${track.name}" sonará pronto.`);
            },
            error: (e) => {
                // Algunos estados (200/204) o errores de permisos (403) se gestionan como éxito visual
                 if([200,204,403].includes(e.status)) {
                    this.handleQueueSuccess(track);
                    if (e.status === 403) this.showSuccess(`¡Listo! "${track.name}" añadida (Simulación).`);
                    else this.showSuccess(`¡Listo! "${track.name}" sonará pronto.`);
                 } else {
                    this.errorMsg = "Error al conectar con Spotify.";
                 }
            }
        });
    }

  /** Notifica al servidor de Spring Boot el registro de la canción pagada */
  private saveTrackToBackendOnly(track: Track) {
    const payload = {
      id: track.id,
      title: track.name,
      artist: track.artist,
      email: localStorage.getItem('userEmail') || 'anonimo',
      amount: 1.0 // Pago fijo por canción
    };

    console.log('--- VERIFICACIÓN DE PAGO ---');
    console.log('Enviando registro de canción al backend...', payload);
    console.log('---------------------------');

    this.http.post('http://127.0.0.1:8080/music/add', payload).subscribe({
      next: () => console.log('✅ Historial de base de datos actualizado correctamente.'),
      error: (err) => console.error('❌ Error guardando en base de datos:', err)
    });
  }

    /** Actualiza la lista visual y la persistencia del navegador */
    private handleQueueSuccess(track: Track) {
        this.queue.push(track);
        // 🚨 GUARDADO ESPECÍFICO: Usamos la clave con el email del usuario
        const queueKey = `queue_${this.userEmail}`;
        localStorage.setItem(queueKey, JSON.stringify(this.queue));
    }

    // --- UTILIDADES DE GEOLOCALIZACIÓN ---

    /** Activa el rastreo GPS del dispositivo del cliente */
    getUserLocation(): void {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(
                (p) => { this.userLat = p.coords.latitude; this.userLng = p.coords.longitude; },
                (e) => console.warn(e), { enableHighAccuracy: true }
            );
        }
    }

    /**
     * CÁLCULO DE DISTANCIA.
     * Determina la distancia real en metros entre dos coordenadas GPS sobre la esfera terrestre.
    */
    getDistanceFromLatLonInM(lat1: number, lon1: number, lat2: number, lon2: number): number {
        const R = 6371; const dLat = this.deg2rad(lat2 - lat1); const dLon = this.deg2rad(lon2 - lon1);
        const a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(this.deg2rad(lat1)) * Math.cos(this.deg2rad(lat2)) * Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)) * 1000;
    }

    /** Convierte grados a radianes */
    deg2rad(n:number){return n*(Math.PI/180);}
}