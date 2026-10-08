<script setup lang="ts">
// ======================================================================
// AppTooltip POR DEMOSTRACION
// El wrapper vive en components/AppTooltip.vue. Esta seccion solo existe
// para ensearlo.
//
// Lo que se ve aqui:
//
//   1. El uso normal: AppTooltip envuelve al elemento y solo lleva texto.
//      AppTooltip pone el lado, el retardo y la flecha por el camino.
//
//   2. Los cuatro lados, con la misma flecha que en la aplicacion real.
//      La flecha es lo que dice de donde va el tooltip: sin ella, uno
//      deduce el lado por donde ha aparecido el texto.
//
//   3. El retardo. UTooltip arranca en 700ms (el default de Reka), que se
//      nota como un parpadeo lento. AppTooltip lo baja a 300ms.
//
//   4. El atajo de teclado, con la prop `kbds`. Es lo que hace util un
//      tooltip en un boton de icono: no repite el icono, enseña el atajo.
//
//   5. Contenido rico con el slot #content, cuando el texto se queda corto.
//
//   6. Un icono que cambia con el estado `open`, que el slot default
//      recibe de UTooltip.
//
// --------------------------------------------------------------------------
// LO QUE UN TOOLTIP NO ES
// No es un aviso y no es una etiqueta. Un tooltip desaparece en cuanto el
// raton se va, asi que no vale para un error de validacion, un precio, ni
// un dato que haya que leer con calma: eso es UAlert, o el slot #body de
// un modal, y no hay forma de repetir el hover.
//
// En movil no hay hover, asi que un tooltip no se ve nunca: solo aparece
// al enfocar, y un movil sin teclado externo casi nunca enfoca. Por eso el
// uso serio de esto es escritorio.
const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

const sides = [
  { side: 'top', label: 'Arriba', icon: 'i-lucide-arrow-up' },
  { side: 'right', label: 'Derecha', icon: 'i-lucide-arrow-right' },
  { side: 'bottom', label: 'Abajo', icon: 'i-lucide-arrow-down' },
  { side: 'left', label: 'Izquierda', icon: 'i-lucide-arrow-left' }
] as const

const shortcuts = [
  { label: 'Guardar', icon: 'i-lucide-save', keys: [{ value: '⌘' }, { value: 'S' }] },
  { label: 'Buscar', icon: 'i-lucide-search', keys: [{ value: '⌘' }, { value: 'K' }] },
  { label: 'Ayuda', icon: 'i-lucide-circle-help', keys: [{ value: '?' }] }
]
</script>

<template>
  <UPageSection
    id="tooltips"
    title="Tooltips"
    description="UTooltip viene con @nuxt/ui y funciona sin configurar nada. AppTooltip solo decide los defaults: 300ms en vez de 700, flecha siempre, y el lado arriba. Para eso no hace falta tocar un solo archivo de configuración."
    :ui="ui"
  >
    <div class="flex flex-col gap-10">
      <div class="bg-muted flex flex-wrap items-center gap-2 rounded-xl border border-default p-5">
        <AppTooltip text="Guardar los cambios">
          <UButton
            label="Guardar"
            icon="i-lucide-save"
            size="sm"
          />
        </AppTooltip>

        <AppTooltip
          text="Texto corto"
          side="right"
        >
          <UButton
            label="A la derecha"
            size="sm"
            variant="outline"
          />
        </AppTooltip>

        <AppTooltip
          text="Con flecha y a 300ms"
          side="bottom"
          :delay="300"
        >
          <UButton
            label="Abajo"
            size="sm"
            variant="subtle"
          />
        </AppTooltip>

        <AppTooltip
          text="Desactivado"
          :disabled="true"
        >
          <UButton
            label="Este no tiene tooltip"
            size="sm"
            color="neutral"
            variant="ghost"
            disabled
          />
        </AppTooltip>
      </div>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="item in sides"
          :key="item.side"
          class="bg-elevated flex flex-col items-center gap-3 rounded-xl border border-default p-5"
        >
          <AppTooltip
            :text="`Salta por ${item.side}`"
            :side="item.side"
          >
            <UButton
              :icon="item.icon"
              color="neutral"
              variant="outline"
              size="lg"
              block
              square
            />
          </AppTooltip>

          <code class="text-dimmed text-xs">
            {{ item.label }}
          </code>
        </div>
      </div>

      <div class="bg-elevated flex flex-col gap-6 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            El retardo se nota, pero hay que medirlo
          </h3>

          <p class="text-muted text-sm">
            Pasa el ratón por encima y cronometra. 700ms (el default de Reka) se
            percibe como lentitud; 300ms como respuesta.
          </p>
        </div>

        <div class="flex flex-wrap items-center gap-3">
          <AppTooltip
            text="Retardo de 700ms, el de UTooltip sin wrapper"
            :delay="700"
            side="right"
          >
            <UButton
              label="700ms"
              size="sm"
              variant="outline"
            />
          </AppTooltip>

          <AppTooltip
            text="Retardo de 300ms, el de AppTooltip"
            :delay="300"
            side="right"
          >
            <UButton
              label="300ms"
              size="sm"
            />
          </AppTooltip>

          <AppTooltip
            text="Retardo de 0ms, sin espera"
            :delay="0"
            side="right"
          >
            <UButton
              label="0ms"
              size="sm"
              variant="ghost"
            />
          </AppTooltip>
        </div>
      </div>

      <div class="bg-elevated flex flex-col gap-6 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            El atajo es la razón de ser del tooltip
          </h3>

          <p class="text-muted text-sm">
            Un icono de guardar no dice cómo se guarda. Con
            <code class="text-primary">kbds</code>, el tooltip enseña el atajo
            y no repite el icono.
          </p>
        </div>

        <div class="flex flex-wrap gap-3">
          <AppTooltip
            v-for="item in shortcuts"
            :key="item.label"
            :text="item.label"
            :kbds="item.keys"
          >
            <UButton
              :icon="item.icon"
              color="neutral"
              variant="outline"
              square
            />
          </AppTooltip>
        </div>
      </div>

      <div class="bg-elevated flex flex-col gap-6 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            Cuando el texto no cabe
          </h3>

          <p class="text-muted text-sm">
            La prop <code class="text-primary">text</code> es una sola cadena.
            Para algo con formato, o para meter algo pulsable, el slot
            <code class="text-primary">#content</code> sustituye el cuerpo
            entero.
          </p>
        </div>

        <div class="flex flex-wrap gap-3">
          <AppTooltip side="bottom">
            <template #content>
              <div class="flex flex-col gap-2">
                <p class="font-semibold">
                  Despliegue en curso
                </p>

                <p class="max-w-64 text-xs opacity-80">
                  El slot #content acepta lo que quieras. Esto solo funciona si
                  el tooltip aguanta abierto con el ratón dentro, que es lo que
                  hace `hoverable`.
                </p>
              </div>
            </template>

            <UButton
              label="Tooltip con contenido"
              icon="i-lucide-info"
              size="sm"
              variant="subtle"
            />
          </AppTooltip>
        </div>
      </div>

      <div class="bg-elevated flex flex-col gap-6 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            El slot default recibe el estado
          </h3>

          <p class="text-muted text-sm">
            UTooltip pasa <code class="text-primary">open</code> a su slot
            default, así que el disparador puede saber si está abierto sin
            adivinar con el ratón.
          </p>
        </div>

        <div>
          <AppTooltip
            text="El icono cambia mientras el tooltip está abierto"
            side="top"
          >
            <template #default="{ open }">
              <UButton
                :icon="open ? 'i-lucide-bell-ring' : 'i-lucide-bell'"
                :color="open ? 'primary' : 'neutral'"
                variant="outline"
                square
              />
            </template>
          </AppTooltip>
        </div>
      </div>
    </div>
  </UPageSection>
</template>
