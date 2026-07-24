package ec.utn.gol.services;

import ec.utn.gol.models.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

@Stateless
public class PrediccionService {

    @PersistenceContext(unitName = "UTNGolCoinPU")
    private EntityManager em;

    private static final java.util.Set<String> PRONOSTICOS_VALIDOS =
            java.util.Set.of("LOCAL", "EMPATE", "VISITANTE");

    public Prediccion crearPrediccion(Long billeteraId, Long partidoId, String pronostico,
            BigDecimal monto, String fechaHoraPartidoIso) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        if (pronostico == null || !PRONOSTICOS_VALIDOS.contains(pronostico)) {
            throw new IllegalArgumentException("Pronóstico inválido, debe ser LOCAL, EMPATE o VISITANTE");
        }

        // Lock pesimista: sin esto, dos requests casi simultáneos para la misma
        // billetera (doble clic, dos pestañas, reintento de red) podían leer el
        // mismo saldo antes de que el primero confirmara, pasar ambos la
        // validación de saldo suficiente, y dejar el saldo negativo (double-spend).
        // El lock también serializa el chequeo+insert de "predicción duplicada" de
        // más abajo para esta misma billetera, cerrando esa segunda carrera también.
        Billetera b = em.find(Billetera.class, billeteraId, LockModeType.PESSIMISTIC_WRITE);
        if (b == null) {
            throw new IllegalArgumentException("Billetera no encontrada");
        }
        if (b.getSaldo().compareTo(monto) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        // RF17 — rechazar predicciones después de iniciado el partido. Antes esta
        // validación se salteaba en silencio si la fecha venía nula o mal formada,
        // lo que permitía apostar sobre partidos ya iniciados: ahora se rechaza
        // la predicción también en esos casos (fail closed, no fail open).
        if (fechaHoraPartidoIso == null || fechaHoraPartidoIso.isBlank()) {
            throw new IllegalArgumentException("Falta la fecha/hora del partido para validar el cierre de apuestas");
        }
        try {
            java.time.Instant inicioPartido = java.time.Instant.parse(fechaHoraPartidoIso);
            if (java.time.Instant.now().isAfter(inicioPartido)) {
                throw new IllegalArgumentException("El partido ya inició, no se pueden crear más predicciones.");
            }
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha/hora del partido con formato inválido");
        }

        List<Prediccion> existentes = em.createQuery(
                "SELECT p FROM Prediccion p WHERE p.billeteraId = :bid AND p.partidoId = :pid", Prediccion.class)
                .setParameter("bid", billeteraId)
                .setParameter("pid", partidoId)
                .getResultList();
        if (!existentes.isEmpty()) {
            throw new IllegalArgumentException("Ya existe una predicción para este partido");
        }

        b.setSaldo(b.getSaldo().subtract(monto));
        em.merge(b);

        Transaccion t = new Transaccion();
        t.setBilleteraId(billeteraId);
        t.setTipo("PREDICCION");
        t.setMonto(monto.negate());
        t.setDescripcion("Predicción partido #" + partidoId);
        em.persist(t);

        Prediccion p = new Prediccion();
        p.setBilleteraId(billeteraId);
        p.setPartidoId(partidoId);
        p.setPronostico(pronostico);
        p.setMonto(monto);
        // Cuota fija 2x para cualquier pronóstico — decisión de alcance académico,
        // no un cálculo pendiente: no hay ninguna fuente de probabilidades reales
        // (odds de casa de apuestas, historial, etc.) en el sistema para derivar una
        // cuota dinámica por resultado. Se fija explícitamente acá (en vez de
        // depender del valor por defecto del campo en la entidad) para que quede
        // claro que es intencional, no un olvido.
        p.setCuota(new BigDecimal("2.0"));
        em.persist(p);

        return p;
    }

    public void liquidarPredicciones(Long partidoId, String resultadoReal) {
        // Antes solo se chequeaba que resultadoReal no fuera null/vacío: un typo
        // ("local" en minúscula, o cualquier valor que no matchee) hacía que TODAS
        // las predicciones pendientes de ese partido cayeran en el else y quedaran
        // "PERDIDA" en silencio, sin que nadie cobrara ni hubiera ningún error.
        if (!PRONOSTICOS_VALIDOS.contains(resultadoReal)) {
            throw new IllegalArgumentException(
                    "resultadoReal inválido, debe ser LOCAL, EMPATE o VISITANTE");
        }

        // Lock pesimista sobre las predicciones a liquidar: si /liquidar/{id} se
        // invoca dos veces casi simultáneamente para el mismo partido (reintento
        // de red, doble notificación desde EstadisticasAPI), sin esto ambas
        // llamadas podían leer las mismas predicciones PENDIENTE antes de que la
        // primera confirmara, y pagar el premio dos veces.
        List<Prediccion> predicciones = em.createQuery(
                "SELECT p FROM Prediccion p WHERE p.partidoId = :pid AND p.estado = 'PENDIENTE'", Prediccion.class)
                .setParameter("pid", partidoId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        for (Prediccion p : predicciones) {
            if (p.getPronostico().equals(resultadoReal)) {
                p.setEstado("GANADA");
                BigDecimal premio = p.getMonto().multiply(p.getCuota());
                Billetera b = em.find(Billetera.class, p.getBilleteraId(), LockModeType.PESSIMISTIC_WRITE);
                if (b == null) {
                    // La billetera de esta predicción ya no existe: no se puede
                    // pagar, pero no se debe abortar la liquidación completa del
                    // partido por una fila huérfana — se deja PENDIENTE para
                    // revisión manual y se sigue con el resto.
                    continue;
                }
                b.setSaldo(b.getSaldo().add(premio));
                em.merge(b);

                Transaccion t = new Transaccion();
                t.setBilleteraId(p.getBilleteraId());
                t.setTipo("PREMIO");
                t.setMonto(premio);
                t.setDescripcion("Premio predicción partido #" + partidoId);
                em.persist(t);
            } else {
                p.setEstado("PERDIDA");
            }
            em.merge(p);
        }
    }

    public List<Prediccion> getPrediccionesByBilletera(Long billeteraId) {
        return em.createQuery("SELECT p FROM Prediccion p WHERE p.billeteraId = :bid ORDER BY p.fechaHora DESC", Prediccion.class)
                .setParameter("bid", billeteraId)
                .getResultList();
    }

    public List<Object[]> getPartidosConMasPredicciones() {
        return em.createQuery(
                "SELECT p.partidoId, COUNT(p) as cantidad FROM Prediccion p "
                + "GROUP BY p.partidoId ORDER BY cantidad DESC")
                .setMaxResults(10)
                .getResultList();
    }
}
