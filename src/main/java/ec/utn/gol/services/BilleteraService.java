package ec.utn.gol.services;

import ec.utn.gol.models.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Stateless
public class BilleteraService {

    @PersistenceContext(unitName = "UTNGolCoinPU")
    private EntityManager em;

    public Billetera crearBilletera(Long usuarioId, String nombreUsuario) {
        Billetera existente = getBilleteraByUsuarioId(usuarioId);
        if (existente != null) {
            throw new IllegalArgumentException("Ya existe una billetera para este usuario");
        }

        Billetera b = new Billetera();
        b.setUsuarioId(usuarioId);
        b.setNombreUsuario(nombreUsuario);
        b.setSaldo(new BigDecimal("10"));
        em.persist(b);

        Transaccion t = new Transaccion();
        t.setBilleteraId(b.getId());
        t.setTipo("BONO_BIENVENIDA");
        t.setMonto(new BigDecimal("10"));
        t.setDescripcion("Bono de bienvenida UTNGolCoin");
        em.persist(t);

        return b;
    }

    public Billetera getBilleteraByUsuarioId(Long usuarioId) {
        try {
            return em.createQuery("SELECT b FROM Billetera b WHERE b.usuarioId = :uid", Billetera.class)
                    .setParameter("uid", usuarioId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public List<Transaccion> getTransacciones(Long billeteraId) {
        return em.createQuery("SELECT t FROM Transaccion t WHERE t.billeteraId = :bid ORDER BY t.fechaHora DESC", Transaccion.class)
                .setParameter("bid", billeteraId)
                .getResultList();
    }

    public boolean aplicarBonoDiario(Long billeteraId) {
        Billetera b = em.find(Billetera.class, billeteraId);
        if (b == null || b.getSaldo().compareTo(BigDecimal.ZERO) > 0) return false;

        LocalDate hoy = LocalDate.now();
        List<BonoDiario> bonos = em.createQuery(
                "SELECT bd FROM BonoDiario bd WHERE bd.billeteraId = :bid AND bd.fecha = :fecha", BonoDiario.class)
                .setParameter("bid", billeteraId)
                .setParameter("fecha", hoy)
                .getResultList();

        if (!bonos.isEmpty()) return false;

        b.setSaldo(b.getSaldo().add(BigDecimal.ONE));
        em.merge(b);

        BonoDiario bd = new BonoDiario();
        bd.setBilleteraId(billeteraId);
        bd.setFecha(hoy);
        em.persist(bd);

        Transaccion t = new Transaccion();
        t.setBilleteraId(billeteraId);
        t.setTipo("BONO_DIARIO");
        t.setMonto(BigDecimal.ONE);
        t.setDescripcion("Bono diario anti-bancarrota");
        em.persist(t);

        return true;
    }

    public BigDecimal getTotalCirculacion() {
        return em.createQuery(
                "SELECT COALESCE(SUM(b.saldo), 0) FROM Billetera b", BigDecimal.class)
                .getSingleResult();
    }

    // RF21 — ranking ordenado por saldo y por aciertos (predicciones GANADA)
    public List<Map<String, Object>> getRanking() {
        List<Billetera> billeteras = em.createQuery("SELECT b FROM Billetera b", Billetera.class)
                .getResultList();

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Billetera b : billeteras) {
            Long aciertos = em.createQuery(
                    "SELECT COUNT(p) FROM Prediccion p WHERE p.billeteraId = :bid AND p.estado = 'GANADA'", Long.class)
                    .setParameter("bid", b.getId())
                    .getSingleResult();

            Map<String, Object> entry = new HashMap<>();
            entry.put("nombreUsuario", b.getNombreUsuario());
            entry.put("saldo", b.getSaldo());
            entry.put("aciertos", aciertos);
            resultado.add(entry);
        }

        resultado.sort((a, bb) -> {
            int cmpSaldo = ((BigDecimal) bb.get("saldo")).compareTo((BigDecimal) a.get("saldo"));
            if (cmpSaldo != 0) return cmpSaldo;
            return ((Long) bb.get("aciertos")).compareTo((Long) a.get("aciertos"));
        });

        return resultado;
    }
}