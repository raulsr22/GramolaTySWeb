package edu.uclm.es.gramola.dao;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import edu.uclm.es.gramola.model.Track;

/**
 * REPOSITORIO PARA EL HISTORIAL DE REPRODUCCIÓN (TABLA TRACKS).
 * * Esta interfaz gestiona la persistencia de todas las canciones que los clientes
 * añaden a la cola mediante pago previo. Es una pieza clave para la validación 
 * de la práctica, ya que permite verificar que los pagos se traducen en 
 * registros reales en la base de datos.
 */
@Repository
public interface TrackDao extends CrudRepository<Track, Long> {
    /**
     * Al extender de CrudRepository<Track, Long>, la interfaz hereda la capacidad 
     * de realizar operaciones sobre la tabla 'tracks' utilizando el 'internalId' 
     * autoincremental como clave primaria.
     * * Métodos disponibles automáticamente:
     * - save(Track): Inserta un nuevo registro en el historial tras un pago exitoso.
     * - findAll(): Permite recuperar todo el historial para auditoría o estadísticas.
     * - count(): Útil para saber cuántas canciones se han servido en total.
     */
}