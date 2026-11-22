<script setup lang="ts">
// ======================================================================
// PÁGINA 403 (SIN PERMISOS)
//
// Distinta del 401: aquí el usuario SÍ tiene sesión, pero su rol no es ADMIN.
// El middleware de /admin/** redirige aquí. Se ofrece salir o volver al
// portfolio; no se le manda al login porque ya está autenticado.
//
// Se muestra el email de la sesión para que quede claro CON QUÉ CUENTA se ha
// entrado: es lo que explica el 403 cuando alguien arrastra una sesión
// antigua (o entró con la cuenta equivocada) y cree que sí es administrador.
// ======================================================================
definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Sin permisos — pepedev', robots: 'noindex' })

const { user } = useUserSession()
const { logout } = useLogout()

// Cerrar sesión llama al BFF, así que el botón se marca mientras tanto para
// que no se dispare dos veces con dobles pulsaciones.
const signingOut = ref(false)

async function handleLogout() {
  signingOut.value = true
  try {
    await logout()
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <UPageCard class="w-full max-w-md text-center">
    <UIcon
      name="i-lucide-shield-off"
      class="text-error mx-auto size-10"
      aria-hidden="true"
    />

    <h1 class="text-highlighted text-xl font-semibold">
      Acceso denegado
    </h1>

    <p class="text-muted text-sm">
      Tu cuenta no tiene permisos de administración para ver esta sección.
    </p>

    <p
      v-if="user?.email"
      class="text-muted text-xs"
    >
      Has iniciado sesión como
      <span class="text-default font-medium">{{ user.email }}</span>.
    </p>

    <template #footer>
      <div class="flex justify-center gap-2">
        <UButton
          to="/"
          label="Ir al portfolio"
          icon="i-lucide-house"
          color="neutral"
          variant="subtle"
        />
        <UButton
          label="Cerrar sesión"
          icon="i-lucide-log-out"
          color="neutral"
          variant="ghost"
          :loading="signingOut"
          @click="handleLogout"
        />
      </div>
    </template>
  </UPageCard>
</template>
