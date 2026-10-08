<script setup lang="ts">
// ======================================================================
// AppDrawer POR DEMOSTRACION
// El cajon reusable vive en components/AppDrawer.vue. Esta seccion solo
// existe para enseñarlo.
//
// Lo que se ve aqui:
//
//   1. El uso mas basico posible: v-model:open, title, y nada mas. Todo lo
//      demas tiene un valor por defecto sensato.
//
//   2. El lado cambia con el ancho. Un menu lateral en horizontal cubre
//      bien el escritorio, pero en vertical se lleva media pantalla y
//      tapa el contenido que hay detras; abajo, donde esta el pulgar, es
//      lo que se espera. Este drawer hace la cuenta con useMediaQuery en
//      lugar de tener dos drawers y decidir cual abrir.
//
//   3. Un #footer sustituido. Por defecto AppDrawer pone un boton de
//      cerrar, que es lo que casi todos necesitan; cuando el cajon es
//      suyo, el slot entero se sustituye.
//
// --------------------------------------------------------------------------
// POR QUE `side` Y NO `direction`
// UDrawer llama a esa prop `direction`. AppDrawer la llama `side` porque es
// la palabra que usa el resto de la aplicacion, y la traduce en un punto.
// UDrawer tambien decide el tirador de arrastre segun el lado: solo lo
// muestra en top y bottom, que es donde tiene sentido arrastrar.
const toast = useToast()

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

// 768px es el breakpoint `md` de Tailwind, el mismo que usan las clases
// de esta pagina. Si uno se move y el otro no, el menu cambia de lado a
// media clase de distancia de donde deberia.
const isDesktop = useMediaQuery('(min-width: 768px)')

const menuOpen = ref(false)
const panelOpen = ref(false)

const drawerSide = computed(() => isDesktop.value ? 'right' : 'bottom')

// Solo ids que existen de verdad en las secciones de la pagina. Un enlace
// a un id que nadie define no da error: simplemente no hace nada, que es
// la clase de fallo mas dificil de detectar en una demo.

// El tipo del item. Vive aqui y no dentro de `go`, porque es un bug que se
// paga solo: `go` usa `label` y `icon`, y si su firma declara solo `label`,
// TypeScript no se queja de acceder a `icon` a no ser que el resto del tipo
// se lo de otro sitio. Con `as const`, `menu` tiene el tipo mas fino posible,
// y este interface es lo que tanto `menu` como `go` comparten.
interface DrawerMenuItem {
  label: string
  icon: string
  to: string
}

// `menu` no lleva `as const`: no lo necesita. Con const, los literales tipo
// '#texto' se vuelven literales y `to` deja de ser string, que es lo unico
// que el codigo usa. Sin const, el tipo de cada item es DrawerMenuItem.
const menu: DrawerMenuItem[] = [
  { label: 'Texto', icon: 'i-lucide-type', to: '#texto' },
  { label: 'Imagenes', icon: 'i-lucide-image', to: '#imagenes' },
  { label: 'Avisos', icon: 'i-lucide-bell', to: '#toasts' },
  { label: 'Modales', icon: 'i-lucide-maximize-2', to: '#modales' },
  { label: 'Cajones', icon: 'i-lucide-panel-right', to: '#cajones' }
]

const notes = [
  {
    title: 'El tirador va solo en vertical.',
    note: 'UDrawer lo muestra unicamente si direction es top o bottom, porque arrastrar tiene sentido en el eje del borde. En right y left no sale.'
  },
  {
    title: 'Cerrar es el estado del padre.',
    note: 'AppDrawer no decide cuando abrir ni cuando cerrar: traduce. Si el estado viviera dentro, dos botones en sitios distintos no podrian abrir el mismo cajon.'
  },
  {
    title: 'El cierre por arrastre tambien avisa.',
    note: 'Deslizar el dedo emite update:open pero no close. Sin traducirlo en onOpenChange, el padre creeria que el cajon sigue abierto.'
  }
] as const

// Abrir un enlace cierra el cajon. Sin esto, en `bottom` el usuario navega
// y el cajon se queda encima de la pagina a la que acaba de ir, porque el
// estado es del padre y nadie lo ha tocado.
function go(item: DrawerMenuItem) {
  menuOpen.value = false

  toast.add({
    title: `Ir a ${item.label}`,
    description: 'El cajón se cierra al navegar, no se queda encima.',
    icon: item.icon,
    color: 'primary'
  })
}
function closePanel() {
  panelOpen.value = false

  toast.add({
    title: 'Panel cerrado',
    description: 'El evento close se emitió una sola vez.',
    icon: 'i-lucide-circle-check',
    color: 'success'
  })
}
</script>

<template>
  <UPageSection
    id="cajones"
    title="Cajones"
    description="El cajón es el modal que entra por un lado. Se usa para lo que tiene su propia pantalla y ademas cabe al lado: un menu, un detalle, un formulario corto. AppDrawer envuelve a UDrawer y traduce su `direction` a `side`."
    :ui="ui"
  >
    <div class="flex flex-col gap-10">
      <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
        <UButton
          label="Abrir menú"
          icon="i-lucide-menu"
          size="sm"
          @click="menuOpen = true"
        />

        <UButton
          label="Abrir panel"
          icon="i-lucide-panel-right"
          size="sm"
          color="neutral"
          variant="subtle"
          @click="panelOpen = true"
        />
      </div>

      <div class="bg-elevated flex flex-col gap-4 rounded-xl border border-default p-6">
        <div class="flex items-center gap-2">
          <UIcon
            name="i-lucide-info"
            class="text-primary size-4 shrink-0"
          />

          <h3 class="text-highlighted font-semibold">
            {{ isDesktop ? 'Lado actual: right' : 'Lado actual: bottom' }}
          </h3>
        </div>

        <div class="flex flex-col gap-3">
          <p
            v-for="(item, index) in notes"
            :key="item.title"
            class="flex gap-3"
          >
            <code class="text-dimmed w-5 shrink-0 text-xs tabular-nums">{{ index + 1 }}</code>

            <span class="text-muted text-sm">
              <span class="text-default font-medium">{{ item.title }}</span>
              {{ item.note }}
            </span>
          </p>
        </div>
      </div>
    </div>

    <!-- Uso minimo: sin side, sale por la derecha y trae su boton de cerrar. -->
    <AppDrawer
      v-model:open="menuOpen"
      title="Menú"
      description="Navegación de la demo"
    >
      <template #body>
        <nav class="flex flex-col gap-1">
          <UButton
            v-for="item in menu"
            :key="item.label"
            :label="item.label"
            :icon="item.icon"
            color="neutral"
            variant="ghost"
            block
            class="justify-start"
            @click="go(item)"
          />
        </nav>
      </template>
    </AppDrawer>

    <!-- El lado responde al ancho y el pie es del consumidor. -->
    <AppDrawer
      v-model:open="panelOpen"
      title="Detalles"
      :description="`Se abre por ${drawerSide}`"
      :side="drawerSide"
      close-label="Cerrar panel"
      @close="closePanel"
    >
      <template #body>
        <div class="flex flex-col gap-3">
          <p class="text-muted text-sm">
            En horizontal sale por la derecha y el contenido se lee al lado.
            En vertical sale por abajo, donde llega el pulgar y sin tapar
            media pantalla.
          </p>

          <UAlert
            color="neutral"
            variant="subtle"
            icon="i-lucide-ruler"
            :title="`direction: '${drawerSide}'`"
            description="Un solo componente, dos comportamientos. Sin duplicarlo."
          />
        </div>
      </template>

      <template #footer>
        <div class="flex w-full flex-col gap-2 sm:flex-row sm:justify-end">
          <UButton
            label="Cancelar"
            color="neutral"
            variant="ghost"
            @click="panelOpen = false"
          />

          <UButton
            label="Aplicar"
            icon="i-lucide-check"
            @click="panelOpen = false"
          />
        </div>
      </template>
    </AppDrawer>
  </UPageSection>
</template>
