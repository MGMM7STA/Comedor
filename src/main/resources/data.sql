CREATE OR REPLACE VIEW v_asistencia AS
SELECT
    r.id_residente,
    r.codigo_acceso,
    CONCAT(r.nombre, ' ', r.apellido) AS residente,
    r.pabellon,
    t.fecha,
    t.tipo AS comida,
    CASE
        WHEN r.fecha_ingreso IS NOT NULL AND t.fecha < r.fecha_ingreso
            THEN 'AUN_NO_ERA_RESIDENTE'
        WHEN EXISTS (
                SELECT 1 FROM marcacion m
                WHERE m.id_residente = r.id_residente
                  AND m.id_turno = t.id_turno
                  AND m.estado = 'PERMITIDO'
                  AND (m.anulada IS NULL OR m.anulada = 0))
            THEN 'ASISTIO'
        WHEN EXISTS (
                SELECT 1 FROM ausencia_detalle ad
                JOIN ausencia a ON a.id_ausencia = ad.id_ausencia
                WHERE a.id_residente = r.id_residente
                  AND ad.fecha = t.fecha
                  AND ad.tipo_comida = t.tipo)
            THEN 'JUSTIFICADO'
        WHEN t.estado <> 'CERRADO'
            THEN 'TURNO_NO_OCURRIO'
        ELSE 'FALTO'
    END AS resultado
FROM residente r
CROSS JOIN turno t
WHERE r.estado = 'ACTIVO';
