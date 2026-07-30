package com.upeu.comedorupeu.repository;

import com.upeu.comedorupeu.models.Residente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResidenteRepository extends JpaRepository<Residente, Long> {

    Optional<Residente> findByCodigoAcceso(String codigoAcceso);

    Optional<Residente> findByTokenAcceso(String tokenAcceso);

    boolean existsByCodigoAcceso(String codigoAcceso);

    boolean existsByDni(String dni);
    Optional<Residente> findFirstByDni(String dni);

    long countByEstado(String estado);
    long countByPabellon(String pabellon);
    long countByEstadoAndPabellon(String estado, String pabellon);

    List<Residente> findByEstadoOrderByApellidoAsc(String estado);
    List<Residente> findByEstadoAndPabellonOrderByApellidoAsc(String estado, String pabellon);
    List<Residente> findByPabellonOrderByApellidoAsc(String pabellon);

    @Query("SELECT r FROM Residente r WHERE (LOWER(CONCAT(r.nombre, ' ', r.apellido)) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(r.codigoAcceso) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "AND (:pabellon IS NULL OR r.pabellon = :pabellon) ORDER BY r.apellido")
    List<Residente> buscar(@Param("q") String q, @Param("pabellon") String pabellon);
}
