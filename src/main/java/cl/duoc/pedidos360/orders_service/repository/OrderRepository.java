package cl.duoc.pedidos360.orders_service.repository;

import cl.duoc.pedidos360.orders_service.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUsuarioIdOrderByCreatedAtDesc(String usuarioId);
}
