<script setup lang="ts">
import { useReducedMotion, useSpring } from '@vueuse/motion'

// ======================================================================
// LO QUE CSS NO PUEDE HACER
// Las secciones anteriores usan transiciones CSS, y para una entrada o una
// salida eso es suficiente: CSS interpola entre dos estados y ya esta.
//
// El limite aparece cuando el valor tiene que seguir moviendose despues de
// cambiar de objetivo. Una transicion no recuerda como iba: al invertirla,
// reinicia la curva desde velocidad cero, y se nota como tiron. Un spring si
// la recuerda, porque en cada paso lee la velocidad actual y continua desde
// ahi. Esta en el codigo del modulo: en useSpring, cada animacion arranca con
// velocity: motionValue.getVelocity().
//
// Eso, y lo de abajo, es lo unico que justifica una libreria aqui.
// RevealOnScroll sigue con CSS a proposito: no hay continuidad que preservar, y
// una transicion de 700ms no necesita fisica.
//
// NOTA DE IMPLEMENTACION, porque sale de los dos bugs que tuvo esta pagina:
// en Tailwind v4 las utilidades translate-x-* NO escriben la propiedad
// transform, escriben la propiedad `translate` (con las variables
// --tw-translate-x/y). Por eso transition-transform no anima un translate-x-*
// y hay que usar transition-[translate]. Y el porcentaje de translate-x se
// resuelve contra el tamano del PROPIO elemento, no contra el del contenedor:
// en un cubo de 2.5rem, translate-x-[100%] no lo mueve ni un pixel.
const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

const prefersReduced = useReducedMotion()

const TRACK = 260

// --------------------------------------------------------------------------
// DEMO 1: el mismo recorrido, con y sin memoria
const active = ref(false)

const springBox = reactive({ x: 0 })
const { set: setSpringBox } = useSpring(springBox, {
  stiffness: 260,
  damping: 18
})

function toggleTrack() {
  active.value = !active.value
  const target = active.value ? TRACK : 0

  if (prefersReduced.value) {
    springBox.x = target
    return
  }

  setSpringBox({ x: target })
}

// --------------------------------------------------------------------------
// DEMO 2: cortar una animacion a mitad y quedarse ahi
//
// apply/stop de useMotion no lo uso aqui: depende de variantes y es mas
// fragil de mantener. El mismo efecto sale de useSpring, cuyo stop congela
// los valores donde esten. La diferencia con CSS es que no se cancela: se
// detiene y el valor final sigue siendo consultable, asi que se puede relajar
// desde ahi en cualquier momento.
const freezeBox = reactive({ x: 0 })
const { set: setFreeze, stop: stopFreeze } = useSpring(freezeBox, {
  // Amortiguacion baja a proposito: el recorrido es largo y va con
  // oscilacion, para que haya un "medio" visible donde cortar.
  stiffness: 70,
  damping: 8
})

function launchFreeze() {
  stopFreeze()
  setFreeze({ x: freezeBox.x > TRACK / 2 ? 0 : TRACK })
}

function holdFreeze() {
  stopFreeze()
}

// --------------------------------------------------------------------------
// DEMO 3: gesto a fisica
const dragCard = reactive({ x: 0, y: 0 })
const dragging = ref(false)
const pointerOrigin = { x: 0, y: 0 }
const cardOrigin = { x: 0, y: 0 }

const { set: setCard } = useSpring(dragCard, {
  stiffness: 320,
  damping: 26
})

function onPointerDown(event: PointerEvent) {
  if (prefersReduced.value) {
    return
  }

  dragging.value = true
  pointerOrigin.x = event.clientX
  pointerOrigin.y = event.clientY
  cardOrigin.x = dragCard.x
  cardOrigin.y = dragCard.y
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (!dragging.value || prefersReduced.value) {
    return
  }

  setCard({
    x: cardOrigin.x + (event.clientX - pointerOrigin.x),
    y: cardOrigin.y + (event.clientY - pointerOrigin.y)
  })
}

function onPointerUp(event: PointerEvent) {
  if (!dragging.value) {
    return
  }

  dragging.value = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture(event.pointerId)
  setCard({ x: 0, y: 0 })
}

// --------------------------------------------------------------------------
// DEMO 4: reordenar posiciones
//
// Al barajar, cada fila va a una nueva coordenada y todas se desplazan a la
// vez. En CSS esto no se puede: un cambio de layout no es una transicion, hay
// que medir antes y despues (FLIP) y hacerlo a mano en JavaScript. Con
// resortes no hace falta medir nada: se le dice a cada uno a donde va y el
// camino lo pone la fisica. Y si se baraja en pleno vuelo, cada fila continua
// desde donde iba.
const ROW_H = 52
const ROW_GAP = 10

const rows = Array.from({ length: 5 }, () => reactive({ y: 0 }))
const rowSetters = rows.map(
  row => useSpring(row, { stiffness: 240, damping: 24 }).set
)
const order = ref([0, 1, 2, 3, 4])

function shuffleRows() {
  const next = [...order.value]

  for (let i = next.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const a = next[i]!
    const b = next[j]!
    next[i] = b
    next[j] = a
  }

  order.value = next
  next.forEach((id, index) => {
    rowSetters[id]?.({ y: index * (ROW_H + ROW_GAP) })
  })
}

function resetRows() {
  order.value = [0, 1, 2, 3, 4]
  order.value.forEach((id, index) => {
    rowSetters[id]?.({ y: index * (ROW_H + ROW_GAP) })
  })
}

// --------------------------------------------------------------------------
// DEMO 5: el valor en vivo
//
// El numero del centro no es el objetivo: es el valor real de la animacion en
// este frame, y la inclinacion sale de el. Con CSS no hay forma de leer el
// valor por el que va una transicion: el valor por el que va solo existe
// dentro del motor de animacion del navegador. Aqui es una variable de
// JavaScript y se puede consultar, transformar y usar para lo que sea.
const liveBox = reactive({ x: 0 })
const { set: setLive } = useSpring(liveBox, {
  stiffness: 120,
  damping: 20
})

function pokeLive() {
  setLive({ x: liveBox.x > TRACK / 2 ? 0 : TRACK })
}

// --------------------------------------------------------------------------
// DEMO 6: amortiguacion
//
// Mismo recorrido, mismo stiffness, y solo cambia damping. Con damping bajo el
// resorte oscila y se va apagando; con damping alto llega y se para en seco.
// En CSS esto no es una opcion: la curva la defines tu y el mismo easing se
// aplica a cualquier duracion, no se puede pedir "oscila hasta que se apague".
// Los tres presets tienen su propio resorte porque los parametros se fijan al
// crear el resorte, no se pueden cambiar en caliente.
const presets = [
  { id: 'bland', label: 'Bland', damping: 26, stiffness: 200, note: 'Llega y se para. Sin oscilacion.' },
  { id: 'lively', label: 'Liviano', damping: 12, stiffness: 200, note: 'Un par de rebotes y para.' },
  { id: 'bouncy', label: 'Rebotador', damping: 4, stiffness: 200, note: 'Oscila un rato hasta apagarse.' }
] as const

const activePreset = ref<(typeof presets)[number]['id']>('bland')

const presetBoxes = Object.fromEntries(
  presets.map(p => [p.id, reactive({ x: 0 })])
) as Record<(typeof presets)[number]['id'], { x: number }>

const presetSetters = Object.fromEntries(
  presets.map(p => [
    p.id,
    useSpring(presetBoxes[p.id], { damping: p.damping, stiffness: p.stiffness }).set
  ])
) as Record<(typeof presets)[number]['id'], (v: { x: number }) => void>

function runPreset(id: (typeof presets)[number]['id']) {
  if (prefersReduced.value) {
    presetBoxes[id].x = presetBoxes[id].x > TRACK / 2 ? 0 : TRACK
    return
  }

  presetSetters[id]?.({ x: presetBoxes[id].x > TRACK / 2 ? 0 : TRACK })
}
</script>

<template>
  <UPageSection
    title="Lo que CSS no puede hacer"
    description="Todo lo de arriba son transiciones CSS, y para entrar y salir sobran. Aquí cambia la pregunta: qué pasa cuando el valor tiene que seguir moviéndose después de cambiar de objetivo. Eso necesita física, y es lo único que justifica una librería de animación."
    :ui="ui"
  >
    <div class="flex flex-col gap-14">
      <!-- DEMO 1 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El mismo recorrido, con y sin memoria
          </h3>

          <UButton
            label="Alternar"
            size="sm"
            variant="subtle"
            @click="toggleTrack"
          />
        </div>

        <p class="text-muted text-sm">
          Pulsa <strong>Alternar</strong> varias veces seguidas, sin esperar a que
          termine. Arriba el cubo nunca se frena de golpe: el resorte lee su
          velocidad y sigue desde ahí. Abajo reinicia la curva desde cero en cada
          cambio de sentido. Los dos recorren los mismos
          {{ TRACK }} px.
        </p>

        <div class="grid gap-4 md:grid-cols-2">
          <div class="bg-muted flex flex-col gap-3 rounded-xl border border-default p-5">
            <div class="flex items-center gap-2">
              <UBadge
                label="resorte"
                size="sm"
                color="primary"
                variant="subtle"
              />

              <span class="text-toned text-xs">useSpring</span>
            </div>

            <div class="bg-default relative h-14 overflow-hidden rounded-lg border border-default">
              <div
                class="bg-primary absolute top-1/2 size-8 rounded-md"
                :style="{ transform: `translateX(${springBox.x}px) translateY(-50%)` }"
              />
            </div>
          </div>

          <div class="bg-muted flex flex-col gap-3 rounded-xl border border-default p-5">
            <div class="flex items-center gap-2">
              <UBadge
                label="transition"
                size="sm"
                color="neutral"
                variant="subtle"
              />

              <span class="text-toned text-xs">clase de Tailwind</span>
            </div>

            <!--
              transition-[translate] y no transition-transform: Tailwind v4
              escribe la propiedad `translate`, no `transform`. Con
              transition-transform esto no se moveria, que fue el primer bug de
              esta pagina. Y el destino va en px, no en porcentaje: el 100% de
              translate-x se resuelve contra el propio cubo de 2rem, no contra
              el contenedor, y daria 0.
            -->
            <div class="bg-default relative h-14 overflow-hidden rounded-lg border border-default">
              <div
                class="bg-primary absolute top-1/2 size-8 rounded-md transition-[translate] duration-300 ease-out"
                :class="active ? 'translate-x-[260px]' : 'translate-x-0'"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- DEMO 2 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Cortar a mitad y quedarse ahí
        </h3>

        <p class="text-muted text-sm">
          Lanza el recorrido y páralo a mitad. El cubo se congela en el valor que
          tenía en ese instante, y ese valor sigue siendo consultable: se puede
          relajar desde ahí cuando quieras. Con una transición CSS la única forma
          de pararla es cancelar el cambio, y no puedes preguntar por el valor por
          el que iba. Aquí la amortiguación es baja a propósito, para que el punto
          donde se corta se vea.
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <div class="flex flex-wrap gap-2">
            <UButton
              label="Lanzar"
              size="sm"
              @click="launchFreeze"
            />

            <UButton
              label="Parar aquí"
              size="sm"
              color="neutral"
              variant="subtle"
              @click="holdFreeze"
            />
          </div>

          <div class="bg-default relative h-14 overflow-hidden rounded-lg border border-default">
            <div
              class="bg-primary absolute top-1/2 size-8 rounded-md"
              :style="{ transform: `translateX(${freezeBox.x}px) translateY(-50%)` }"
            />
          </div>
        </div>
      </div>

      <!-- DEMO 3 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Gesto a física
        </h3>

        <p class="text-muted text-sm">
          Arrastra la tarjeta. El cubo persigue al puntero con un resorte en vez de
          seguirlo pegado, y al soltar vuelve con un rebote corto. En CSS habría que
          traducir el evento a una transición con un retardo fijo, y el retardo no
          puede depender de la velocidad del gesto: uno rápido y uno lento darían el
          mismo retraso, que es justo lo que hace que un arrastre se sienta pegado o
          flotante.
        </p>

        <div class="bg-muted flex flex-col gap-3 rounded-xl border border-default p-5">
          <div class="bg-default relative flex h-36 items-center justify-center overflow-hidden rounded-lg border border-default">
            <div
              class="bg-primary flex size-14 touch-none items-center justify-center rounded-xl select-none"
              :class="dragging ? 'cursor-grabbing' : 'cursor-grab'"
              :style="{ transform: `translate(${dragCard.x}px, ${dragCard.y}px)` }"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
            >
              <UIcon
                name="i-lucide-move"
                class="text-inverted size-6"
              />
            </div>
          </div>

          <p class="text-toned text-xs">
            Con <code>prefers-reduced-motion</code> activo el arrastre se desactiva
            y el bloque se coloca directamente en su sitio.
          </p>
        </div>
      </div>

      <!-- DEMO 4 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            Reordenar posiciones
          </h3>

          <div class="flex gap-2">
            <UButton
              label="Barajar"
              size="sm"
              @click="shuffleRows"
            />

            <UButton
              label="Ordenar"
              size="sm"
              color="neutral"
              variant="subtle"
              @click="resetRows"
            />
          </div>
        </div>

        <p class="text-muted text-sm">
          Cada fila va a una coordenada nueva y todas se desplazan a la vez. En CSS
          un cambio de layout no se puede animar: hay que medir la posición antes y
          después y corregirla a mano. Aquí no se mide nada, se le dice a cada resorte
          a dónde va y el camino lo pone la física. Baraja dos veces seguidas y
          mira cómo continúa cada fila desde donde iba.
        </p>

        <div class="bg-muted rounded-xl border border-default p-5">
          <div
            class="bg-default relative overflow-hidden rounded-lg border border-default"
            style="height: 300px"
          >
            <div
              v-for="id in order"
              :key="id"
              class="bg-primary/90 text-inverted absolute left-2 flex items-center gap-2 rounded-md px-3 text-sm font-medium"
              :style="{
                transform: `translateY(${rows[id]?.y}px)`,
                height: `${ROW_H}px`
              }"
            >
              <span>Fila {{ id + 1 }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- DEMO 5 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          El valor en vivo
        </h3>

        <p class="text-muted text-sm">
          El número del medio no es el destino: es el valor real de la animación en
          este frame, y la inclinación del cubo sale de él. Con CSS no hay forma de
          leer el valor por el que va una transición, porque ese valor solo existe
          dentro del motor del navegador. Aquí es una variable de JavaScript: se
          consulta, se transforma y se usa para lo que sea.
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <p class="text-toned text-sm">
              x = <span class="text-highlighted font-mono text-lg tabular-nums">{{ Math.round(liveBox.x) }}</span>
            </p>

            <UButton
              label="Empujar"
              size="sm"
              @click="pokeLive"
            />
          </div>

          <div class="bg-default relative h-14 overflow-hidden rounded-lg border border-default">
            <div
              class="bg-primary absolute top-1/2 size-8 rounded-md"
              :style="{
                transform: `translateX(${liveBox.x}px) translateY(-50%) rotate(${liveBox.x / 14}deg)`
              }"
            />
          </div>
        </div>
      </div>

      <!-- DEMO 6 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Amortiguación
        </h3>

        <p class="text-muted text-sm">
          Mismo recorrido y mismo <code class="text-toned">stiffness</code>; solo
          cambia <code class="text-toned">damping</code>. Con amortiguación baja el
          resorte oscila y se va apagando; con alta llega y se para en seco. En CSS
          esto no existe: la curva la defines tú y el mismo
          <code class="text-toned">easing</code> se aplica a cualquier duración. No
          se puede pedir «oscila hasta que se apague».
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <div class="flex flex-wrap gap-2">
            <UButton
              v-for="preset in presets"
              :key="preset.id"
              :label="preset.label"
              size="sm"
              :variant="activePreset === preset.id ? 'solid' : 'subtle'"
              :color="activePreset === preset.id ? 'primary' : 'neutral'"
              @click="activePreset = preset.id; runPreset(preset.id)"
            />
          </div>

          <div class="bg-default relative h-14 overflow-hidden rounded-lg border border-default">
            <div
              class="bg-primary absolute top-1/2 size-8 rounded-md"
              :style="{
                transform: `translateX(${presetBoxes[activePreset].x}px) translateY(-50%)`
              }"
            />
          </div>

          <p class="text-muted text-sm">
            {{ presets.find(p => p.id === activePreset)?.note }}
          </p>
        </div>
      </div>

      <!-- Nota -->
      <div class="bg-elevated flex flex-col gap-4 rounded-xl border border-default p-6">
        <div class="flex items-center gap-2">
          <UIcon
            name="i-lucide-info"
            class="text-primary size-4 shrink-0"
          />

          <h3 class="text-highlighted font-semibold">
            Por qué el resto no usa esto
          </h3>
        </div>

        <p class="text-muted text-sm">
          Para una entrada de 700 ms, un resorte no aporta nada y cuesta mantenerlo
          sincronizado con el render del servidor. Por eso
          <code class="text-toned">RevealOnScroll</code> sigue con CSS. La librería
          entra solo donde el valor tiene que seguir moviéndose entre
          interacciones, que es lo único que una transición no resuelve.
        </p>

        <p class="text-muted text-sm">
          Las seis demos usan <code class="text-toned">useSpring</code>, con objetos
          reactivos y refs: nada de <code class="text-toned">v-motion</code> sobre
          el markup. Y todas consultan
          <code class="text-toned">prefers-reduced-motion</code>, igual que el
          aviso 4 de <code class="text-toned">main.css</code>.
        </p>
      </div>
    </div>
  </UPageSection>
</template>
