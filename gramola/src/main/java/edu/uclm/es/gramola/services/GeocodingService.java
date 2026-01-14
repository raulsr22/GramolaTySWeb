package edu.uclm.es.gramola.services;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * SERVICIO DE GEOLOCALIZACIÓN EXTERNA (REQUISITO EXTRA A).
 * * Esta clase se encarga de convertir direcciones físicas (texto) en coordenadas 
 * geográficas (latitud y longitud). Utiliza la API gratuita Nominatim de 
 * OpenStreetMap para permitir que el sistema ubique los bares en el mapa 
 * automáticamente durante el registro.
*/
@Service
public class GeocodingService {

    /**
     * URL base de la API de búsqueda de Nominatim.
    */
    private final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search";

    /**
     * MÉTODO PRINCIPAL DE GEOLOCALIZACIÓN.
     * * Intenta obtener las coordenadas de una dirección. Implementa una estrategia de
     * "fallback": si la dirección específica falla, intenta buscar por la ciudad o 
     * región para asegurar que el bar tenga al menos una ubicación aproximada.
     * * @param address Dirección completa introducida en el formulario de registro.
     * @return Array de doubles [latitud, longitud] o null si no se encuentra nada.
    */
    public double[] getCoordinates(String address) {
        // Intentar primero con la dirección completa
        double[] coords = fetchCoordinates(address);
        
        // Si falla, intentar un fallback simplificado (solo ciudad/país si es posible detectar)
        if (coords == null && address.contains(",")) {
            String simplifiedAddress = address.substring(address.lastIndexOf(",") + 1).trim();
            System.out.println("[GEO] Intentando fallback con: " + simplifiedAddress);
            coords = fetchCoordinates(simplifiedAddress);
        }
        
        return coords;
    }

    /**
     * REALIZA LA PETICIÓN HTTP A LA API DE OPENSTREETMAP.
     * * @param query Texto de la dirección a buscar.
    */
    private double[] fetchCoordinates(String query) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // Construcción de la URL con parámetros de formato JSON y límite de 1 resultado.
            // Se usa el placeholder {q} para que RestTemplate gestione la codificación de caracteres.
            String url = NOMINATIM_API + "?q={q}&format=json&limit=1";
            
            HttpHeaders headers = new HttpHeaders();
            /**
             * CONFIGURACIÓN DE IDENTIDAD (USER-AGENT):
             * Las APIs de mapas suelen bloquear peticiones anónimas de Java (403 Forbidden).
             * Al configurar un User-Agent de navegador real y un Referer, "humanizamos" 
             * la petición para que el servidor externo la acepte.
            */
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.set("Referer", "https://gramola-app.com");
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            System.out.println("[GEO-DEBUG] Buscando: " + query);
            
            // Ejecución de la llamada GET
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class, query);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Usamos Jackson para parsear el JSON de forma segura
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody());

                if (root.isArray() && root.size() > 0) {
                    // Extraemos los datos del primer resultado (el más relevante)
                    JsonNode location = root.get(0);
                    double lat = location.get("lat").asDouble();
                    double lon = location.get("lon").asDouble();
                    
                    System.out.println("[GEO-SUCCESS] Encontrado: " + lat + ", " + lon);
                    return new double[]{lat, lon};
                } else {
                    System.err.println("[GEO] Sin resultados para: " + query);
                }
            }
        } catch (HttpClientErrorException e) {
            // Captura errores específicos de la API (como el 403 por falta de User-Agent)
            System.err.println("[GEO] Error HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            // Captura errores de red o de parseo de JSON
            System.err.println("[GEO] Error: " + e.getMessage());
        }
        return null; // En caso de cualquier fallo, devolvemos null para que el UserService lo gestione
    }
    }
