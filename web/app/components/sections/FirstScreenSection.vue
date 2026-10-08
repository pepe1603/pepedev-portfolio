<script setup lang="ts">
// ======================================================================
// LA PRIMERA PANTALLA
// El caso que ninguna otra seccion muestra: que pasa con un bloque que ya
// esta en pantalla cuando la pagina carga.
//
// El componente arranca con isVisible = false, asi que el SERVIDOR siempre
// pinta el bloque en su estado oculto. No hay excepcion para lo que esta
// por encima del fold. Eso tiene dos consecuencias que conviene ver:
//
//   1. Sin JavaScript, el contenido no aparece nunca. El estado inicial es
//      opacity-0 y no hay nada que lo quite. Es el precio de animar la
//      primera pantalla, y se paga entero en el bloque de arriba.
//   2. Con JavaScript, el observer dispara al montar y la animacion corre
//      entera. En el bloque de arriba se ve como una carga; en uno mas
//      abajo, como un reveal normal.
//
// Esta seccion es la que hay que leer antes de poner un RevealOnScroll en
// el HeroSection. Las demas explican como se anima; esta explica si debe
// animarse.
//
// Los tres bloques de abajo son el mismo componente con distinta duracion,
// para comparar de un vistazo cuanto se tarda en ver el contenido. El
// primero va con once, que es lo que corresponde a lo que esta encima del
// fold: si no, el bloque desaparece al bajar y hay que subir otra vez.
const variants = [
  {
    id: 'once',
    duration: 500,
    once: true,
    note: 'once. Es lo que corresponde a la primera pantalla: entra una vez y no vuelve a irse al bajar.',
    badge: 'primera pantalla'
  },
  {
    id: 'repeats',
    duration: 500,
    once: false,
    note: 'once: false. Baja y sube: el bloque se oculta al salir y vuelve a entrar. En un bloque alto se nota bien.',
    badge: 'repetido'
  },
  {
    id: 'long',
    duration: 1400,
    once: true,
    note: '1400ms. El mismo reveal, el doble de duración: se ve que el hueco no es del observer sino de la transición.',
    badge: 'lenta'
  }
] as const

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}
</script>

<template>
  <UPageSection
    title="La primera pantalla"
    description="Todas las secciones anteriores muestran bloques que entran al hacer scroll. Esta muestra el caso que faltaba: qué pasa con el bloque que ya está en pantalla al cargar, que es donde el servidor y el navegador discrepan."
    :ui="ui"
  >
    <div class="flex flex-col gap-8">
      <div
        v-for="variant in variants"
        :key="variant.id"
        class="flex flex-col gap-3"
      >
        <div class="flex flex-wrap items-center gap-3">
          <UBadge
            :label="variant.badge"
            color="neutral"
            variant="subtle"
            size="sm"
          />

          <code class="text-toned text-xs">
            :duration="{{ variant.duration }}" :once="{{ variant.once }}"
          </code>
        </div>

        <!--
          min-h-72 a proposito: con un bloque mas bajo que el viewport se ve
          entrar y salir pegado al borde, que no demuestra nada. Con uno que
          ocupa la pantalla entera la entrada y la salida quedan separadas.
        -->
        <RevealOnScroll
          :duration="variant.duration"
          :once="variant.once"
        >
          <div class="flex min-h-72 flex-col justify-center gap-3 rounded-xl bg-muted border border-default p-8">
            <p class="text-highlighted text-xl font-semibold">
              {{ variant.id }}
            </p>

            <p class="text-muted max-w-2xl">
              {{ variant.note }}
            </p>
          </div>
        </RevealOnScroll>
      </div>

      <div class="bg-elevated flex flex-col gap-4 rounded-xl border border-default p-6">
        <div class="flex items-center gap-2">
          <UIcon
            name="i-lucide-info"
            class="text-primary size-4 shrink-0"
          />

          <h3 class="text-highlighted font-semibold">
            El coste de animar la primera pantalla
          </h3>
        </div>

        <p class="text-muted text-sm">
          El componente empieza con <code class="text-toned">isVisible = false</code>, así que el servidor
          escribe <code class="text-toned">opacity-0</code> en el HTML del bloque. Sin JavaScript, ese contenido
          no aparece nunca: no hay nada que quite el estado inicial. Con JavaScript, el observer dispara al
          montar y todo entra bien.
        </p>

        <p class="text-muted text-sm">
          Por eso <code class="text-toned">HeroSection</code> va envuelto con
          <code class="text-toned">once</code> y una duración corta: si además se repitiera al bajar, el bloque
          desaparecería de la vista y el visitante vería el hueco al volver arriba.
        </p>

        <p class="text-muted text-sm">
          Para contenido que deba verse sin JavaScript, la alternativa es aplicar el estado oculto solo después
          de <code class="text-toned">onMounted</code>, a costa de un flash en la primera pantalla. No se hace
          aquí porque en esta plantilla el contenido estático es la prioridad.
        </p>
      </div>
    </div>
  </UPageSection>
</template>
