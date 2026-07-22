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

    public Prediccion crearPrediccion(Long billeteraId, Long partidoId, String pronostico,
            BigDecimal monto, String fechaHoraPartidoIso) {
        Billetera b = em.find(Billetera.class, billeteraId);
        if (b == null) {
            throw new IllegalArgumentException("Billetera no encontrada");
        }
        if (b.getSaldo().compareTo(monto) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        // RF17 — rechazar predicciones después de iniciado el partido
        if (fechaHoraPartidoIso != null && !fechaHoraPartidoIso.isBlank()) {
            try {
                java.time.Instant inicioPartido = java.time.Instant.parse(fechaHoraPartidoIso);
                if (java.time.Instant.now().isAfter(inicioPartido)) {
                    throw new IllegalArgumentException("El partido ya inició, no se pueden crear más predicciones.");
                }
            } catch (java.time.format.DateTimeParseException e) {
                // si la fecha viene en formato inválido, no bloqueamos por eso
            }
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
        em.persist(p);

        return p;
    }

    public void liquidarPredicciones(Long partidoId, String resultadoReal) {
        List<Prediccion> predicciones = em.createQuery(
                "SELECT p FROM Prediccion p WHERE p.partidoId = :pid AND p.estado = 'PENDIENTE'", Prediccion.class)
                .setParameter("pid", partidoId)
                .getResultList();

        for (Prediccion p : predicciones) {
            if (p.getPronostico().equals(resultadoReal)) {
                p.setEstado("GANADA");
                BigDecimal premio = p.getMonto().multiply(p.getCuota());
                Billetera b = em.find(Billetera.class, p.getBilleteraId());
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
