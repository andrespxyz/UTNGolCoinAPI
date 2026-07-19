package ec.utn.gol.services;

import ec.utn.gol.models.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Stateless
public class BilleteraService {

    @PersistenceContext(unitName = "UTNGolCoinPU")
    private EntityManager em;

    public Billetera crearBilletera(Long usuarioId, String nombreUsuario) {
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

    public List<Billetera> getRanking() {
        return em.createQuery("SELECT b FROM Billetera b ORDER BY b.saldo DESC", Billetera.class)
                .getResultList();
    }
}