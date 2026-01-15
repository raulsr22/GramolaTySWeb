import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpHeaders} from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * SERVICIO CENTRAL DE INTEGRACIÓN CON SPOTIFY.
 * * Este servicio gestiona el ciclo de vida completo de la comunicación con Spotify:
 * 1. Flujo de Autorización (OAuth 2.0): Construcción de URLs y enlace de tokens.
 * 2. Consumo de API: Gestión de dispositivos, playlists, búsqueda y control de reproducción.
*/
@Injectable({
  providedIn: 'root'
})
export class Spoty {
  // URL de autorización de Spotify 
  private authorizeUrl: string = 'https://accounts.spotify.com/authorize';
  // URL de redireccionamiento (misma que la configurada en la app de Spotify)
  private redirectUrl: string = 'http://127.0.0.1:4200/callback';
  // URL del backend para solicitar el Access Token 
  private baseUrl: string = 'http://localhost:8080/spoti';
  // URL base para el consumo directo de la API de Spotify
  private spotifyApiUrl: string = 'https://api.spotify.com/v1';
  
  /** * LISTA DE PERMISOS (SCOPES).
   * Define las capacidades que el establecimiento otorga a la Gramola.
   * Incluye control de reproducción, lectura de estado y acceso a librerías privadas.
  */
  private scopes: string[] = [
    'user-read-private', 
    'user-read-email', 
    'playlist-read-private', 
    'playlist-read-collaborative', 
    'user-read-playback-state', 
    'user-modify-playback-state', 
    'user-read-currently-playing', 
    'user-library-read', 
    'user-library-modify', 
    'user-read-recently-played', 
    'user-top-read', 
    'app-remote-control', 
    'streaming'
  ];

  constructor(private http: HttpClient) {}

  /**
   * Genera una cadena alfanumérica aleatoria.
   * Se utiliza como medida de seguridad (parámetro 'state') para evitar ataques 
   * durante el proceso de redirección OAuth.
  */
  private generateString(length: number = 16): string {
    let result = '';
    const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    for (let i = 0; i < length; i++) {
      result += characters.charAt(Math.floor(Math.random() * characters.length));
    }
    return result;
  }
  
  /**
   * PASO 1 OAUTH: CONSTRUCCIÓN DE LA URL DE AUTORIZACIÓN.
   * * Prepara la redirección del navegador hacia Spotify.
   * @param clientId El ID público de la aplicación Spotify del bar.
   * @returns URL completa con parámetros codificados para el consentimiento.
  */
  public buildAuthorizationUrl(clientId: string): string {
    const state = this.generateString(16);

    // 1. Guardar el 'state' para validarlo al regreso
    localStorage.setItem('oauth_state', state);

    // 2. Construir los parámetros de la URL 
    const params = new HttpParams({
        fromObject: {
            response_type: 'code',
            client_id: clientId,
            // Unimos los permisos por espacios según el estándar OAuth
            scope: this.scopes.join(' '), 
            redirect_uri: this.redirectUrl,
            state: state
        }
    });

    // 3. Devolver la URL completa
    return `${this.authorizeUrl}?${params.toString()}`;
  }

  /**
   * PASO 2 OAUTH: INTERCAMBIO DE CÓDIGO PARA OBTENER EL TOKEN.
   * * Envía el código temporal al backend para obtener el token definitivo.
   * @param code Código recibido en el callback.
   * @param clientId Identificador del cliente para resolución en el servidor.
  */
  public getAuthorizationToken(code: string, clientId: string): Observable<any> {
    const url = `${this.baseUrl}/getAuthorizationToken?code=${code}&clientId=${clientId}`;
    return this.http.get<any>(url);
  }



  // --- LÓGICA DE CONSUMO DE API DE SPOTIFY  ---

  /**
   * Genera las cabeceras de autorización Bearer.
   * @param token El Access Token obtenido de Spotify.
  */
  private getHeaders(token: string): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  /**
   * Carga los dispositivos disponibles del usuario.
   * Endpoint: GET /v1/me/player/devices
   */
  public getDevices(token: string): Observable<any> {
    const headers = this.getHeaders(token);
    const url = `${this.spotifyApiUrl}/me/player/devices`;
    // Spotify devuelve { "devices": [...] }
    return this.http.get<any>(url, { headers });
  }

  /**
   * Carga las playlists del usuario logueado.
   * Endpoint: GET /v1/me/playlists
   */
  public getPlaylists(token: string): Observable<any> {
    const headers = this.getHeaders(token);
    const url = `${this.spotifyApiUrl}/me/playlists`;
    // Spotify devuelve { "items": [...] }
    return this.http.get<any>(url, { headers });
  }

  /**
   * MONITORIZACIÓN DE ESTADO.
   * Obtiene información en tiempo real sobre qué suena, si está en pausa y el progreso.
   * Endpoint: GET /v1/me/player
  */
  getCurrentPlaybackState(token: string): Observable<any> {
    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });
    return this.http.get('https://api.spotify.com/v1/me/player', { headers });
  }

  /**
   * BÚSQUEDA DE CANCIONES.
   * Realiza consultas al catálogo global de música.
   * @param query Texto de búsqueda (canción o artista).
   * Endpoint: GET /search
  */
  public searchTracks(token: string, query: string): Observable<any> {
    const headers = this.getHeaders(token);
    
    // Configurar los parámetros de búsqueda (query y tipo 'track')
    const params = new HttpParams()
        .set('q', query)
        .set('type', 'track')
        .set('limit', '12'); // Limitar a 12 resultados para la interfaz

    const url = `${this.spotifyApiUrl}/search`;
    // Spotify devuelve { "tracks": { "items": [...] } }
    return this.http.get<any>(url, { headers, params });
  }

  /**
   * ACCIÓN DE "COLAR" CANCIÓN (ADD TO QUEUE).
   * Añade una canción para que suene inmediatamente después de la actual.
   * @param trackUri URI de Spotify de la canción (ej: spotify:track:ID).
   * @param deviceId ID del dispositivo del local.
   * Endpoint: POST /me/player/queue
   */
  public addToQueue(token: string, trackUri: string, deviceId: string): Observable<any> {
    const headers = this.getHeaders(token);
    const params = new HttpParams()
        .set('uri', trackUri)
        .set('device_id', deviceId);

    const url = `${this.spotifyApiUrl}/me/player/queue`;
    // Es una petición POST sin cuerpo (null)
    return this.http.post(url, null, { headers, params });
  }

  /**
   * REPRODUCCIÓN DE PLAYLISTS.
   * Cambia la música de fondo del local a una lista completa.
   * @param contextUri URI de la playlist de Spotify.
   * @param deviceId ID del reproductor de salida.
   * Endpoint: PUT /me/player/play
  */
  playContext(token: string, contextUri: string, deviceId: string): Observable<any> {
    const headers = new HttpHeaders({ 'Authorization': `Bearer ${token}` });
    const url = `https://api.spotify.com/v1/me/player/play?device_id=${deviceId}`;
    const body = { context_uri: contextUri };
    return this.http.put(url, body, { headers });
  }
}

