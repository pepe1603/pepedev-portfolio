<script setup lang="ts">
const colorMode = useColorMode()

// Sin View Transitions no hay sobre que animar: se cambia la preferencia y
// ya. Es el camino de Firefox y de cualquier navegador que no implemente la
// API, y no puede quedarse esperando a un temporal que no existe.
function setPreference(value: 'light' | 'dark') {
  colorMode.preference = value
}

// El radio es la distancia del punto del click a la ESQUINA MAS LEJANA, no
// a la esquina 0,0. Con la de 0,0 el circulo llega antes a la esquina
// opuesta que a los bordes de la pantalla y el reveal se ve como si se
// cortara en diagonal.
function farthestCornerRadius(x: number, y: number) {
  return Math.hypot(
    Math.max(x, window.innerWidth - x),
    Math.max(y, window.innerHeight - y)
  )
}

// --------------------------------------------------------------------------
// POR QUE NADA SE ANIMA CON element.animate()
//
// Un pseudo-elemento de View Transitions no es un elemento. No existe en el
// DOM, asi que no tiene instancia de Element y no se le puede pasar a
// document.documentElement.animate(): eso animaria el <html> real, que se
// pinta POR DEBAJO del overlay de la transicion, con lo cual no se veria.
//
// La API CSSPseudoElement de Chrome (125+) tampoco sirve: solo cubre
// ::before y ::after, no los ::view-transition-*.
//
// Asi que la animacion va en CSS, sobre el pseudo-elemento, y desde JS solo
// se le pasan las tres medidas que no estan escritas en ningun sitio:
// --theme-x, --theme-y y --theme-radius. Ver el bloque de View Transitions
// en assets/css/main.css.
// --------------------------------------------------------------------------
function toggleTheme(event: MouseEvent) {
  // Se decide sobre `value`, que es el tema RESUELTO, y no sobre
  // `preference`, que puede ser 'system'. Si preference es 'system' y el
  // sistema esta en dark, value es 'dark' y hay que poner 'light'.
  const newMode = colorMode.value === 'dark' ? 'light' : 'dark'

  if (!document.startViewTransition) {
    setPreference(newMode)
    return
  }

  // Una transicion de pantalla completa con un recorte que se abre es justo
  // el caso que prefers-reduced-motion existe para evitar. Sin esto, el
  // recorte se respeta y la pagina sigue moviendose.
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    setPreference(newMode)
    return
  }

  const x = event?.clientX ?? window.innerWidth / 2
  const y = event?.clientY ?? window.innerHeight / 2

  const root = document.documentElement
  root.style.setProperty('--theme-x', `${x}px`)
  root.style.setProperty('--theme-y', `${y}px`)
  root.style.setProperty('--theme-radius', `${farthestCornerRadius(x, y)}px`)

  // Las tres variables se escriben ANTES de startViewTransition y no dentro
  // de t.ready.then(). La instantanea del estado nuevo se captura en el
  // primer frame posterior al callback, y la animacion arranca ahi
  // tambien: si las variables se pusieran al resolverse ready, la
  // animacion ya habria arrancado sin ellas y leeria el fallback.
  document.startViewTransition(() => {
    setPreference(newMode)
  })
}
</script>

<template>
  <ClientOnly>
    <UButton
      :icon="
        colorMode.value === 'dark' ? 'i-lucide-sun' : 'i-lucide-moon'
      "
      :aria-label="
        colorMode.value === 'dark'
          ? 'Cambiar a tema claro'
          : 'Cambiar a tema oscuro'
      "
      color="neutral"
      variant="ghost"
      class="rounded-full"
      @click="toggleTheme"
    />

    <!-- El icono sale del tema RESUELTO, y en SSR todavia no hay tema, asi
         que no hay nada que pintar en el servidor. El fallback reserva el
         hueco con el mismo tamano del boton para que el header no dé un
         salto al hidratar. -->
    <template #fallback>
      <div class="size-8" />
    </template>
  </ClientOnly>
</template>
