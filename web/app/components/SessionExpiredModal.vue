<script setup lang="ts">
// ======================================================================
// SESSIONEXPIREDMODAL
//
// Aviso de "tu sesión ya no vale" y qué hacer al respecto. Global: se
// monta UNA vez en app.vue, al final del template y fuera de UPage, igual
// que AppCommandPalette.
//
// --------------------------------------------------------------------------
// POR QUÉ SE PUEDE CERRAR SIN ELEGIR
//
// `dismissible` (ESC o clic fuera) queda activo a propósito. La sesión ya
// está muerta en el servidor en el momento en el que esto se pinta: no hay
// nada que proteger. Impedir el cierre no añade seguridad, solo deja al
// usuario atrapado delante de un panel que ya no puede usar.
//
// Lo que sí se quita es el botón X (`:close="false"`): aquí las salidas son
// dos acciones con nombre, y una X en la esquina competiría con ellas.
//
// --------------------------------------------------------------------------
// POR QUÉ LAS DOS ACCIONES SE DIFERENCIAN POR DESTINO
//
// "Volver a entrar" va a /login con la ruta actual en el query, para
// retomar lo que se estaba haciendo. "Cerrar sesión" va al portfolio: es la
// salida de quien decide irse del panel. Si las dos fueran a /login serían
// el mismo botón escrito dos veces.
// ======================================================================

const DEFAULT_DETAIL = 'Por seguridad, cerramos la sesión tras un periodo de inactividad. Vuelve a entrar para continuar.'

const { open, close, detail } = useSessionGuard()
const route = useRoute()

const message = computed(() => detail.value ?? DEFAULT_DETAIL)

// En una pantalla de acceso no tiene sentido mandarse al propio login con un
// redirect al login: acabaría en /admin, que es exactamente donde no puede
// entrar. Ahí se va directo al panel.
const loginTarget = computed(() => route.path.startsWith('/login')
  ? { path: '/admin' }
  : { path: '/login', query: { redirect: route.fullPath } })

async function backToLogin() {
  close()
  await navigateTo(loginTarget.value)
}

async function leavePanel() {
  close()
  await navigateTo('/')
}
</script>

<template>
  <UModal
    v-model:open="open"
    :close="false"
    title="Tu sesión ha expirado"
  >
    <template #body>
      <div class="flex flex-col items-center gap-3 text-center">
        <UIcon
          name="i-lucide-lock-keyhole"
          class="text-warning size-10"
          aria-hidden="true"
        />
        <p class="text-muted text-sm">
          {{ message }}
        </p>
      </div>
    </template>

    <template #footer>
      <div class="flex flex-col gap-2 sm:flex-row-reverse sm:justify-start">
        <UButton
          label="Volver a entrar"
          icon="i-lucide-log-in"
          @click="backToLogin"
        />
        <UButton
          label="Cerrar sesión"
          icon="i-lucide-log-out"
          color="neutral"
          variant="subtle"
          @click="leavePanel"
        />
      </div>
    </template>
  </UModal>
</template>
