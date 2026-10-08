<script setup lang="ts">
// ======================================================================
// AppCommandPalette
// La paleta de comandos global: ⌘K / Ctrl+K, o el boton del header.
//
// --------------------------------------------------------------------------
// QUE HACE ESTE COMPONENTE Y POR QUE ESTA SEPARADO
//
// Es global, asi que se monta UNA vez en app.vue, al final del template y
// fuera de UPage. Si viviera dentro de una pagina, solo habria paleta en
// esa pagina, y una paleta global que depende de la ruta es una paleta
// que no esta.
//
// El estado de "abierto" no vive aqui sino en utils/commandPalette.ts,
// porque el boton del header tambien tiene que abrirlo.
//
// --------------------------------------------------------------------------
// LA ESTRUCTURA: MODAL POR FUERA, PALETTE DENTRO
//
// UCommandPalette NO es un dialogo: es el contenido, sin capa ni foco
// atrapado. Por eso va dentro de UModal, que es quien pone el overlay,
// cierra con Escape y gestiona el foco. El palette solo pinta la lista.
//
// El puente entre los dos es su evento `update:open`, que emite el boton
// de cerrar. Sin ese `@update:open`, la X cerraria el palette por dentro y
// el modal se quedaria abierto encima: una capa invisible que se come los
// clics.
//
// --------------------------------------------------------------------------
// OJO: EL PALETTE NO SE CIERRA AL ELEGIR
//
// A primera vista parece que si, porque Enter elige el item. Pero al
// elegir solo se ejecuta su onSelect: `update:open` unicamente lo emite el
// boton de la X. Sin cerrarlo a mano, elegir "Formulario" navega pero deja
// el modal abierto encima de la pagina nueva.
//
// Por eso TODOS los items, tambien los que tienen `to`, llevan onSelect
// para cerrar. Un item puede tener las dos cosas a la vez: `to` navega y
// onSelect cierra.
//
// --------------------------------------------------------------------------
// LA BUSQUEDA ASINCRONA, Y SUS DOS TRAMPAS
//
// Trampa 1: las respuestas llegan desordenadas.
//
// El debounce de 300ms evita casi todos los casos, pero no todos. Si se
// escribe "for" y 250ms despues "formula", salen las dos peticiones; si la
// de "for" tarda mas, llega DESPUES y pisa los resultados buenos con los
// malos. El sintoma es un palette que se queda con la lista de otro texto.
//
// La solucion es un contador monotonico: cada peticion guarda el numero que
// tenia al salir, y al volver solo aplica su respuesta si sigue siendo la
// ultima. Las tardias se descartan.
//
// Trampa 2: el doble filtrado.
//
// UCommandPalette filtra TODO lo que recibe con Fuse.js, incluidos los
// resultados del servidor. Es decir: el servidor busca "formu", devuelve lo
// que encuentra, y despues Fuse vuelve a buscar "formu" en esa lista ya
// corta. En el mejor caso no pasa nada; en el peor, Fuse descarta algo que
// el servidor encontro bien y el palette dice "sin resultados" con la lista
// llena.
//
// Por eso el grupo de resultados lleva `ignoreFilter: true`, que es una
// prop de GRUPO (no de item) y significa "esto ya viene filtrado". Sin ella,
// la busqueda asincrona es una fuente de resultados fantasma.
//
// --------------------------------------------------------------------------
// POR QUE `searchDelay` SE DEJA EN 0
//
// UCommandPalette trae su propio searchDelay, que es el retardo del Fuse de
// cliente, y por defecto es 0. Subirlo a 300 haria que moverse con las
// flechas tambien tardase: se pulsa una letra y la lista tarda ese tiempo en
// moverse, que se lee como lag. El retardo que importa aqui es el de la red,
// y ese lo pone el watchDebounced de abajo.
const toast = useToast()
const colorMode = useColorMode()

const isOpen = useCommandPalette()

function close() {
  isOpen.value = false
}

async function copyUrl() {
  await navigator.clipboard.writeText(window.location.href)

  toast.add({
    title: 'URL copiada',
    description: 'Ya está en el portapapeles.',
    icon: 'i-lucide-circle-check',
    color: 'success'
  })
}

// ⌘K / Ctrl+K para abrir, y los atajos que el palette promete en sus kbds.
// Una sola llamada: useMagicKeys engancha listeners a window, y llamarlo
// dos veces los duplica. Ver utils/commandPalette.ts, donde esta el por que
// de que no acepte handlers en el objeto de opciones.
useCommandPaletteShortcuts({
  toggleColorMode: () => {
    colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'
  },
  copyUrl
})

const searchTerm = ref('')
const loading = ref(false)
const asyncResults = ref<{
  label: string
  suffix: string
  icon: string
  to: string
}[]>([])

// El numero de peticion en curso. Solo la respuesta con el numero mas alto
// se aplica; el resto se tira. Ver la trampa 1 de arriba.
let requestId = 0

// Los grupos estaticos se declaran una vez y no dependen de nada, asi que
// no son un computed: un computed los recrearia en cada keystroke sin
// motivo, y ademas recrearia los onSelect.
const navigationGroup = {
  id: 'navigation',
  label: 'Navegación',
  items: [
    {
      label: 'Inicio',
      suffix: 'Design system completo',
      icon: 'i-lucide-house',
      to: '/',
      onSelect: close
    },
    {
      label: 'Formulario',
      suffix: 'UForm validado con Zod',
      icon: 'i-lucide-text-cursor-input',
      to: '/formulario',
      onSelect: close
    },
    {
      label: 'Carrusel',
      suffix: 'Tarjetas visibles por breakpoint',
      icon: 'i-lucide-gallery-horizontal-end',
      to: '/carrusel',
      onSelect: close
    }
  ]
}

const actionsGroup = {
  id: 'actions',
  label: 'Acciones',
  items: [
    {
      label: 'Cambiar tema',
      suffix: 'Claro u oscuro',
      icon: 'i-lucide-sun-moon',
      kbds: ['meta', 'shift', 'l'],
      onSelect: () => {
        colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'
        close()
      }
    },
    {
      label: 'Copiar la URL',
      suffix: 'La dirección de esta página',
      icon: 'i-lucide-clipboard',
      kbds: ['meta', 'shift', 'c'],
      onSelect: async () => {
        close()
        await copyUrl()
      }
    }
  ]
}

const groups = computed(() => [
  navigationGroup,
  actionsGroup,
  {
    id: 'results',
    // Etiqueta vacia = UCommandPalette no pinta la cabecera del grupo (solo
    // la pinta si hay texto), asi que el grupo queda limpio mientras no hay
    // nada que enseñar. Y un grupo sin items se salta entero.
    label: searchTerm.value ? 'Resultados' : '',
    // El servidor ya busco: sin esto, Fuse vuelve a filtrar la lista.
    // Ver la trampa 2 de arriba.
    ignoreFilter: true,
    items: asyncResults.value
  }
])

// El debounce va en un watch y no en un v-model debounced porque
// searchTerm es el v-model de UCommandPalette y lo necesita al momento:
// si se desenlazara un instante, el input parpadearia.
watchDebounced(searchTerm, async (query) => {
  const term = query.trim()

  // Sin texto se vacia y se sale. Sin este return, borrar el input dejaria
  // en pantalla los resultados del texto anterior.
  if (!term) {
    asyncResults.value = []
    loading.value = false
    return
  }

  const currentId = ++requestId
  loading.value = true

  try {
    // `query` en vez de concatenar a mano: el texto puede llevar acentos, '&'
    // o '#', y pegado en la URL rompe el separador de parametros. El objeto
    // de query lo escapa.
    const results = await $fetch<typeof asyncResults.value>('/api/search', {
      query: { q: term }
    })

    // Llego una respuesta que ya no es la que se pide: se tira.
    if (currentId !== requestId) {
      return
    }

    asyncResults.value = results
  } catch {
    // Un fallo de red no puede romper el palette: los grupos estaticos
    // siguen validos aunque la busqueda no conteste.
    if (currentId === requestId) {
      asyncResults.value = []
    }
  } finally {
    // El spinner lo apaga solo la peticion que sigue siendo la ultima. Si no,
    // lo apagaria una que ya no refleja lo que hay en pantalla.
    if (currentId === requestId) {
      loading.value = false
    }
  }
}, { debounce: 300 })

// Al cerrar se limpia el texto. Sin esto, se reabre con el input relleno y
// los resultados viejos, que es un descuido que se nota mucho.
watch(isOpen, (open) => {
  if (!open) {
    searchTerm.value = ''
  }
})
</script>

<template>
  <UModal
    v-model:open="isOpen"
    :ui="{ content: 'p-0 sm:p-0' }"
  >
    <template #content>
      <UCommandPalette
        v-model:search-term="searchTerm"
        :groups="groups"
        :loading="loading"
        placeholder="Buscar páginas, secciones y acciones…"
        autofocus
        @update:open="isOpen = $event"
      >
        <template #footer>
          <div class="flex w-full items-center justify-between gap-4">
            <div class="flex items-center gap-4">
              <span class="text-dimmed flex items-center gap-1 text-xs">
                <UKbd>↑</UKbd>
                <UKbd>↓</UKbd>
                para moverse
              </span>

              <span class="text-dimmed flex items-center gap-1 text-xs">
                <UKbd>↵</UKbd>
                para elegir
              </span>
            </div>

            <span class="text-dimmed flex items-center gap-1 text-xs">
              <UKbd>esc</UKbd>
              para cerrar
            </span>
          </div>
        </template>
      </UCommandPalette>
    </template>
  </UModal>
</template>
