<script setup lang="ts">
// ======================================================================
// PÁGINA 403 (SIN PERMISOS)
//
// Distinta del 401: aquí el usuario SÍ tiene sesión, pero su rol no es ADMIN.
// El middleware de /admin/** redirige aquí. Se ofrece salir o volver al
// portfolio; no se le manda al login porque ya está autenticado.
// ======================================================================
definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Sin permisos — pepedev', robots: 'noindex' })

const { logout } = useLogout()
</script>

<template>
  <UPageCard class="w-full max-w-md text-center">
    <UIcon
      name="i-lucide-shield-off"
      class="text-error mx-auto size-10"
    />

    <h1 class="text-highlighted text-xl font-semibold">
      Acceso denegado
    </h1>

    <p class="text-muted text-sm">
      Tu cuenta no tiene permisos de administración para ver esta sección.
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
          @click="logout()"
        />
      </div>
    </template>
  </UPageCard>
</template>
