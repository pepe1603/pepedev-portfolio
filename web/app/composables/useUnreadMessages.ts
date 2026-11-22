// ======================================================================
// CONTADOR DE MENSAJES NO LEÍDOS
//
// Lo comparten dos sitios: el sidebar, que lo pinta como badge en todas las
// páginas del panel, y /admin/mensajes, que lo refresca después de cada
// acción. useState es el sitio natural para eso: un estado reactivo
// compartido, sin peticiones duplicadas y sin depender de que useAsyncData
// deduplique por key (que solo garantiza la key, no que no se relance).
//
// El refresco es a propósito explícito: al montar el layout y después de
// tocar un mensaje, nunca en cada navegación. Un badge que no se actualiza
// solo es preferible a una petición por clic; si llega un mensaje nuevo
// mientras el panel está abierto, se ve al recargar o al volver a entrar.
// ======================================================================
export function useUnreadMessages() {
  const api = useAdminApi()

  // null = todavía no se ha consultado (distinto de 0, que es un dato real).
  const count = useState<number | null>('admin-unread-count', () => null)

  async function refreshUnread() {
    try {
      const result = await api.get<MessageCount>('/messages/unread-count')
      count.value = result.count
    } catch {
      // Un 401 ya lo convierte useAdminApi en el modal de sesión caducada.
      // Cualquier otro fallo no debe tumbar el panel: el badge se queda como
      // estaba y las páginas siguen funcionando.
    }
  }

  return { count, refreshUnread }
}
