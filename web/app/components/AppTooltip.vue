<script setup lang="ts">
// ======================================================================
// AppTooltip: los defaults de UTooltip, pero los que quieres.
//
// UTooltip ya funciona sin configurar nada, asi que este componente no es
// obligatorio: es el sitio donde se decide COMO se ven los tooltips de la
// aplicacion, para que 200 de ellos no pinsen cada uno 300ms.
//
// Los defaults que impone, y por que:
//
//   1. delay 300ms en vez de los 700ms que trae Reka. 700 no es "paciencia",
//      es lento: se nota un parpadeo antes de que aparezca nada. 300 es lo
//      que tardan de sobra las manos en llegar desde el boton.
//
//   2. side 'top' en vez de 'bottom'. UTooltip pone 'bottom' porque es lo
//      habitual en las barras de herramientas, pero el tooltip vive casi
//      siempre encima de un icono dentro de una barra, y ahi abajo choca
//      con el siguiente icono. Arriba solo se sale en el margen de la
//      ventana, que UTooltip ya corrige con collisionPadding.
//
//   3. arrow activada. No es decoracion: la flecha es lo unico que dice en
//      que direccion va el tooltip. Sin ella, uno deduce el lado por la
//      posicion del texto y a veces se equivoca.
//
// --------------------------------------------------------------------------
// OJO CON `side`
// UTooltip NO tiene prop `side`. Va dentro de `content`, junto a
// sideOffset, collisionPadding y demas: es la prop del TooltipContent de
// Reka, no del Root. Este wrapper la sube a primer nivel porque es lo que
// se escribe el 99% de las veces, y la deja pasar tal cual.
//
// La razon de tener el wrapper: el default de UTooltip (700ms, bottom) se
// puede cambiar, pero solo en un sitio y para toda la aplicacion, y en un
// proyecto con 200 tooltips nadie va a buscarlo. Aqui se cambia una vez
// tambien, pero cerca del componente que lo usa y con el motivo escrito.
const open = defineModel<boolean>('open')

const props = withDefaults(defineProps<{
  /** Texto corto. Para contenido rico, usar el slot #content. */
  text?: string
  /** Lado. OJO: UTooltip lo llama content.side. */
  side?: 'top' | 'right' | 'bottom' | 'left'
  /** Milisegundos hasta que aparece. Reka por defecto: 700. */
  delay?: number
  /** Distancia al elemento. Reka por defecto: 8. */
  sideOffset?: number
  /** Muestra la flecha. */
  arrow?: boolean
  /** Desactiva el tooltip sin quitarlo del DOM. */
  disabled?: boolean
  /** Deja meter el raton dentro del tooltip sin que se cierre. */
  hoverable?: boolean
  /** Atajos de teclado, para los iconos que son acciones. */
  kbds?: { value: string }[]
  /** Escape hatch para lo raro de Reka. Gana sobre side y sideOffset. */
  content?: Record<string, unknown>
}>(), {
  side: 'top',
  delay: 300,
  sideOffset: 8,
  arrow: true,
  disabled: false,
  hoverable: true,
  kbds: undefined
})

// El orden importa: `content` va el ultimo para que quien pase algo por
// aqui gane sobre los defaults de arriba. Al reves, un collisionPadding
// propio se perderia en silencio.
const contentProps = computed(() => ({
  side: props.side,
  sideOffset: props.sideOffset,
  ...props.content
}))

// Un tooltip que se cierra en cuanto el raton busca la palabra que quiere
// leer es un tooltip inutil para leer. Reka lo resuelve con su "hoverable
// content", que por defecto esta ACTIVO: el tooltip aguanta abierto cuando
// el raton entra en el. Aqui se llama `hoverable` porque el nombre de Reka
// es una negacion y obliga a pensar dos veces, y se invierte al pasarlo.
//
// El slot default de UTooltip recibe `open`, asi que quien envuelve puede
// cambiar su icono mientras esta abierto. Se deja pasar tal cual.
</script>

<template>
  <UTooltip
    v-model:open="open"
    :text="text"
    :content="contentProps"
    :arrow="arrow"
    :disabled="disabled"
    :delay-duration="delay"
    :disable-hoverable-content="!hoverable"
    :kbds="kbds"
  >
    <slot :open="open" />

    <template
      v-if="$slots.content"
      #content="slotProps"
    >
      <slot
        name="content"
        v-bind="slotProps"
      />
    </template>
  </UTooltip>
</template>
