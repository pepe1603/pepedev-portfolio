-- Paginacion de /admin/messages (Bloque 8): indice compuesto para
-- `WHERE status = ? ORDER BY created_at DESC, id DESC` sin sort en memoria.
CREATE INDEX idx_messages_status_created_at ON messages (status, created_at DESC, id DESC);