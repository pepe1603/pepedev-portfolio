<script setup lang="ts">
// Cada tarjeta monta su RevealOnScroll con un :key propio. Al pulsar replay
// solo cambia el key, Vue destruye el nodo viejo y crea uno nuevo, y el
// observer vuelve a empezar en false: es la unica forma de ver la animacion
// repetida sin tener que salirse de la seccion.
const replays = reactive<Record<string, number>>({
  together: 0,
  lead: 0,
  staged: 0
})

function replay(name: string) {
  replays[name]!++
}

// Las tarjetas animan con DEMO_DURATION, asi que la tabla de retardos sale
// de revealSteps() con ese mismo valor. Si se cambia la duracion aqui, los
// numeros de la tabla se corrigen solos.
const DEMO_DURATION = 900

const sequences = [
  {
    value: 'together',
    note: 'Todo a la vez. Es el reveal clásico: un solo tween y ya está.'
  },
  {
    value: 'lead',
    note: 'Primero aparece y después se coloca. La opacidad lidera al movimiento.'
  },
  {
    value: 'staged',
    note: 'Tres tiempos: fundido, desplazamiento y por último escala y desenfoque.'
  }
] as const

const icons = {
  together: 'i-lucide-align-horizontal-justify-center',
  lead: 'i-lucide-git-merge',
  staged: 'i-lucide-layers'
} as const

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}
</script>

<template>
  <UPageSection
    title="Cadenas de entrada"
    description="Dentro de una misma entrada, cada propiedad puede esperar su turno. Es lo que separa un tween de una secuencia, y se ve sin necesidad de scrollear."
    :ui="ui"
  >
    <div class="grid gap-4 lg:grid-cols-3">
      <div
        v-for="(sequence, index) in sequences"
        :key="sequence.value"
        class="flex flex-col gap-4 rounded-xl bg-elevated border border-default p-5"
      >
        <div class="flex items-center gap-2">
          <UIcon
            :name="icons[sequence.value]"
            class="text-primary size-4 shrink-0"
          />

          <code class="text-sm font-semibold">
            {{ sequence.value }}
          </code>
        </div>

        <p class="text-muted min-h-12 text-sm">
          {{ sequence.note }}
        </p>

        <div class="flex flex-col gap-1.5">
          <div
            v-for="(property, i) in revealProperties"
            :key="property"
            class="flex items-center gap-2 text-xs"
          >
            <span class="text-dimmed w-16">{{ property }}</span>

            <div class="bg-muted h-1.5 flex-1 overflow-hidden rounded-full">
              <div
                class="bg-primary h-full rounded-full"
                :style="{ width: `${100 - revealSteps(sequence.value, DEMO_DURATION)[i]! / (DEMO_DURATION * 0.4) * 100}%` }"
              />
            </div>

            <code class="text-toned w-12 text-right">{{ revealSteps(sequence.value, DEMO_DURATION)[i] }}ms</code>
          </div>
        </div>

        <div class="mt-auto rounded-lg border border-dashed border-accented bg-transparent p-6">
          <RevealOnScroll
            :key="replays[sequence.value]"
            :animation="index === 2 ? 'zoom-in' : 'fade-up'"
            :sequence="sequence.value"
            :duration="DEMO_DURATION"
          >
            <div class="flex h-16 items-center justify-center">
              <span class="text-primary text-sm font-semibold">
                {{ sequence.value }}
              </span>
            </div>
          </RevealOnScroll>
        </div>

        <UButton
          label="Repetir"
          icon="i-lucide-rotate-cw"
          color="neutral"
          variant="outline"
          size="sm"
          block
          @click="replay(sequence.value)"
        />
      </div>
    </div>
  </UPageSection>
</template>
