<script setup lang="ts">
import { h } from 'vue'
// El tipo Toast NO sale de '@nuxt/ui': alli solo se exportan los tipos de
// props de los componentes. El objeto que devuelve add() es el interface
// Toast que declara el propio composable.
import type { Toast } from '@nuxt/ui/composables/useToast'

// ======================================================================
// TOASTS Y AVISOS
// Los avisos de Nuxt UI son dos piezas y conviene no confundirlas:
//
//   1. <UAlert> es una caja EN PAGINA. Vive en el flujo, empuja al resto
//      del contenido y se va con el scroll. Se usa para informacion que
//      el usuario deberia leer aunque no pulse nada: un banner de cookies,
//      un error de validacion pegado al campo, un aviso de mantenimiento.
//
//   2. useToast() es una cola GLOBAL. No esta en el flujo, se monta en el
//      <body> y sobrevive a la navegacion. Se usa para el resultado de una
//      accion: "guardado", "hemos recibido tu mensaje", "la sesion ha
//      caducado". Lo que paso ya, que no se quede ahi.
//
// El error clasico es usarlos al reves: poner el resultado de una accion
// como UAlert y un aviso que hay que leer como toast. El primero desaparece
// al hacer scroll; el segundo se va solo a los 5 segundos.
//
// --------------------------------------------------------------------------
// LO QUE HACE QUE ESTO FUNCIONE SIN CONFIGURAR NADA
//
// <UApp> en app/app.vue es quien monta el <UToaster> que dibuja la cola.
// Sin el, toast.add() funciona, los toasts se acumulan en el estado, y no
// aparece NADA en pantalla: no hay error ni aviso en consola, solo un
// silencio. Es el fallo mas caro de esta API.
//
// El estado de la cola es un useState compartido, no un ref del componente
// que llama a toast.add(). Por eso un toast lanzado desde un composable, un
// middleware o un plugin se ve igual que uno lanzado desde un boton, y por
// eso dos <UApp> en la misma pagina pintarian la misma cola dos veces.
//
// --------------------------------------------------------------------------
// AVISO 1: EL ESTADO ES useState, ASI QUE CRUZA EL SERVIDOR
//
// Si lanzas un toast durante el render del servidor, se serializa en el
// payload y el cliente lo vuelve a pintar al hidratar: el visitante ve el
// aviso dos veces. Los toasts se lanzan en respuesta a una accion del
// usuario, que en el cliente no ocurre, asi que el caso normal es
// inocuo. El que no lo es es un toast de "sesion caducada" puesto en un
// plugin del servidor: aparecera en cada carga de pagina.
//
// --------------------------------------------------------------------------
// AVISO 2: remove() ANIMA, clear() NO
//
// remove(id) marca el toast como cerrado y lo saca del array 200ms despues,
// que es lo que le da tiempo a la transicion de salida. clear() vacia el
// array de golpe. La diferencia se ve al borrar cuatro toasts de golpe:
// desaparecen de golpe en vez de deslizarse. Si el efecto importa,
// quitarlos uno a uno con un bucle y un pequeno retardo entre llamadas.
//
// --------------------------------------------------------------------------
// AVISO 3: AL PASARSE DE max, EL MAS ANTIGUO NO ANIMA
//
// La cola hace un slice(-max) sobre el array. El toast que se cae no pasa
// por open:false, asi que desaparece de un corte. Con max en 5 y una app
// que lanza un toast por cada pulsacion rapida, es normal verlo. No es un
// fallo de animacion: es que ese camino no pasa por Reka UI.
//
// --------------------------------------------------------------------------
// AVISO 4: update() REINICIA EL TEMPORIZADOR
//
// update(id, {...}) hace {...toastViejo, ...toastNuevo} con UNA excepcion:
// duration se copia siempre del objeto nuevo. Si no lo incluyes, se
// escribe undefined y el toast vuelve al valor global (5000ms), aunque le
// hubieras puesto 30000. Con un toast en duration: 0 el efecto es peor:
// un update parcial le pone un reloj de 5 segundos y el aviso se cierra a
// mitad de lo que deberia durar. La demo 5 lo lleva puesto en todos los
// update por eso.
//
// --------------------------------------------------------------------------
// AVISO 5: UN onClick EN LA ACCION HAY QUE PARARLO
//
// Si el toast entero tiene onClick y una de sus acciones tambien, el clic
// en el boton dispara los dos: el onClick de la accion y el del toast. El
// boton va envuelto en un ToastAction que hace stopPropagation por dentro,
// pero eso solo cubre el caso en que la accion NO trae su propio
// onClick. En cuanto lo trae, hay que pararlo a mano.
//
// --------------------------------------------------------------------------
// POR QUE toasts ESTA SUELTO
//
// useToast devuelve un objeto normal, no un ref, asi que `toast.toasts` es
// un Ref dentro de un objeto y Vue NO lo desenvuelve en la plantilla: solo
// desenvuelve los refs que estan en el primer nivel del setup. Con
// `toast.toasts` habia que escribir toasts.value a mano y la plantilla
// tambien. Sacandolo a un binding de primer nivel las dos cosas se
// resuelven solas, que es de donde sale el nombre.
const toast = useToast()
const { toasts } = toast
const options = useToasterOptions()

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

// --------------------------------------------------------------------------
// DEMO 1: el aviso minimo
function showMinimal() {
  toast.add({
    title: 'Cambios guardados',
    description: 'Tu perfil ya está actualizado.'
  })
}

const anatomy = [
  { field: 'title', type: 'string', note: 'La línea gruesa. Sin ella el toast queda mudo.' },
  { field: 'description', type: 'string', note: 'Segunda línea, en tono apagado.' },
  { field: 'icon', type: 'string', note: 'Lo que va a la izquierda. Cierra el círculo de color.' },
  { field: 'color', type: 'ToastProps[\'color\']', note: 'El acento. primary por defecto.' }
] as const

// --------------------------------------------------------------------------
// DEMO 2: color
//
// secondary y neutral estan para aplicaciones con dos tonos de marca;
// el resto son semanticos y es lo que se usa de verdad.
const colors = [
  { value: 'primary', label: 'primary', icon: 'i-lucide-sparkles', title: 'Listo', description: 'Acento de marca, el color por defecto.' },
  { value: 'success', label: 'success', icon: 'i-lucide-circle-check', title: 'Cambios guardados', description: 'La operación terminó bien.' },
  { value: 'info', label: 'info', icon: 'i-lucide-info', title: 'Nueva versión', description: 'Hay una actualización disponible.' },
  { value: 'warning', label: 'warning', icon: 'i-lucide-triangle-alert', title: 'Conexión inestable', description: 'Puede que se corte la sesión.' },
  { value: 'error', label: 'error', icon: 'i-lucide-circle-x', title: 'No se pudo enviar', description: 'El servidor devolvió un error 502.' },
  { value: 'secondary', label: 'secondary', icon: 'i-lucide-star', title: 'Segundo tono', description: 'Solo si tu app tiene dos colores de marca.' },
  { value: 'neutral', label: 'neutral', icon: 'i-lucide-bell', title: 'Aviso neutro', description: 'Sin sémantica: ni bien ni mal.' }
] as const

function showColor(item: typeof colors[number]) {
  toast.add({
    title: item.title,
    description: item.description,
    icon: item.icon,
    color: item.value
  })
}

// --------------------------------------------------------------------------
// DEMO 3: icono, avatar o nada
//
// leading acepta lo que se le pase, asi que tambien admite un nodo. Lo
// habitual es una de estas dos: icon para el estado, avatar para la persona.
function showIcon() {
  toast.add({
    title: 'Archivo subido',
    description: 'informe-q3.pdf · 2,4 MB',
    icon: 'i-lucide-cloud-upload',
    color: 'success'
  })
}

function showAvatar() {
  toast.add({
    title: 'Ana te ha invitado',
    description: 'ana@ejemplo.com se une al equipo de diseño.',
    avatar: {
      // Mismo host que ImagesSection y, sobre todo, mismo host que ya esta
      // en image.domains de nuxt.config.ts. Con un dominio que no este en
      // esa lista, @nuxt/image devuelve la URL tal cual: la imagen se ve,
      // pero sin srcset y sin placeholder, y sin avisar.
      src: 'https://picsum.photos/id/237/400',
      alt: 'Ana'
    }
  })
}

function showPlain() {
  toast.add({
    title: 'Sin icono, sin avatar',
    description: 'El texto se alinea al borde y ya está.'
  })
}

function showCustomClose() {
  toast.add({
    title: 'Se cierra con otro icono',
    description: 'closeIcon cambia solo el botón de la X.',
    closeIcon: 'i-lucide-arrow-right'
  })
}

function showNoClose() {
  toast.add({
    title: 'Sin botón de cerrar',
    description: 'close: false lo esconde. Antes la barra de progreso lo marca.',
    icon: 'i-lucide-timer',
    close: false
  })
}

// --------------------------------------------------------------------------
// DEMO 4: duración y progreso
//
// duration en milisegundos, 0 para que no se cierre solo. La barra es el
// tiempo restante, y con expand en true el raton por encima la pausa.
//
// La nota va en los cuatro en vez de solo en los que la tienen: un array
// con `as const` y objetos de forma distinta es una union, y `item.note`
// sobre una union solo compila si TODOS los miembros la declaran.
const durations = [
  { value: 2000, label: '2 s', note: 'apenas se lee' },
  { value: 5000, label: '5 s', note: 'el valor global' },
  { value: 12000, label: '12 s', note: 'para texto que hay que leer' },
  { value: 0, label: '∞', note: 'no se cierra solo' }
] as const

function showDuration(duration: number) {
  toast.add({
    title: duration === 0 ? 'No se cierra solo' : `Se cierra en ${duration / 1000} s`,
    description: 'Púlsalo y mira cuánto le queda de barra.',
    icon: 'i-lucide-timer',
    duration
  })
}

function showNoProgress() {
  toast.add({
    title: 'Sin barra de progreso',
    description: 'progress: false. Para errores que deben quedarse a la vista.',
    icon: 'i-lucide-wifi-off',
    color: 'error',
    duration: 0,
    progress: false
  })
}

function showForeignProgress() {
  toast.add({
    title: 'La barra con otro color',
    description: 'progress: { color }. El toast puede ser warning y la barra, error.',
    icon: 'i-lucide-gauge',
    color: 'warning',
    duration: 8000,
    progress: { color: 'error' }
  })
}

// --------------------------------------------------------------------------
// DEMO 5: update, el mismo toast cambia de contenido
//
// Es el patrón de todo lo que tarda: se lanza un toast informativo con
// duration 0 y se va rellenando con update a medida que llega la respuesta.
// El toast no desaparece ni parpadea, se transforma.
//
// AVISO: duration hay que pasarlo en CADA update, no solo en el primero.
// update() hace {...toastViejo, ...toastNuevo} pero con una excepción
// dura: duration se copia del objeto nuevo tal cual, y si no viene se
// escribe undefined. O sea que un update parcial de un toast con
// duration: 0 le devuelve el global de 5000ms, y el aviso se cierra a
// mitad de la subida. Es el aviso 4 de arriba, escrito donde se nota.
const progress = ref(0)
let progressTimer: ReturnType<typeof setInterval> | undefined

const uploadToast = ref<string | number | undefined>()

function startUpload() {
  if (uploadToast.value !== undefined) {
    toast.remove(uploadToast.value)
  }

  progress.value = 0

  const t = toast.add({
    title: 'Subiendo archivo',
    description: '0 %',
    icon: 'i-lucide-cloud-upload',
    color: 'primary',
    duration: 0,
    progress: false
  })

  uploadToast.value = t.id

  clearInterval(progressTimer)
  progressTimer = setInterval(() => {
    progress.value = Math.min(100, progress.value + Math.random() * 18)

    if (progress.value >= 100) {
      clearInterval(progressTimer)

      toast.update(t.id, {
        title: 'Archivo subido',
        description: 'informe-q3.pdf · 2,4 MB',
        icon: 'i-lucide-circle-check',
        color: 'success',
        duration: 4000,
        progress: true
      })

      uploadToast.value = undefined
      return
    }

    toast.update(t.id, {
      description: `${Math.round(progress.value)} %`,
      duration: 0
    })
  }, 320)
}

onBeforeUnmount(() => clearInterval(progressTimer))

// --------------------------------------------------------------------------
// DEMO 6: el callback que reutiliza el toast al expirar
//
// onUpdate:open se dispara cuando el estado open cambia, y por tanto
// tambien cuando expira solo. Se usa para encadenar dos estados en el
// MISMO toast: uno informativo que luego se convierte en el resultado.
// Devolver temprano con open en true es lo que corta la recursion: sin
// ese return, el toast que se crea al final vuelve a disparar el callback.
function showCallback() {
  const t = toast.add({
    title: 'Enviando el formulario',
    description: 'Un momento.',
    icon: 'i-lucide-loader-circle',
    duration: 2000,
    // Clave con corchetes en vez de 'onUpdate:open': entrecomillarla obliga a
    // eslint a pedir comillas en TODAS las claves del objeto, y este objeto
    // tiene nueve. La forma con corchetes es la misma para Vue.
    ['onUpdate:open'](open: boolean) {
      if (open) {
        return
      }

      toast.update(t.id, {
        title: 'Formulario enviado',
        description: 'Te respondemos por correo en menos de 24 h.',
        icon: 'i-lucide-circle-check',
        color: 'success',
        ['onUpdate:open']: undefined
      })
    }
  })
}

// --------------------------------------------------------------------------
// DEMO 7: acciones
//
// actions es una lista de props de UButton. En vertical van bajo el
// texto; en horizontal, junto a la X. Cada onClick recibe el evento del
// boton, y hay que pararlo si el toast entero tambien es clicable.
function showActions() {
  toast.add({
    title: 'No se pudo enviar el formulario',
    description: 'Comprueba la conexión e inténtalo otra vez.',
    icon: 'i-lucide-wifi-off',
    color: 'error',
    duration: 0,
    actions: [
      {
        label: 'Reintentar',
        icon: 'i-lucide-refresh-cw',
        color: 'error',
        variant: 'outline',
        onClick: () => {
          toast.add({
            title: 'Reintentando',
            icon: 'i-lucide-loader-circle',
            color: 'primary',
            duration: 1500
          })
        }
      },
      {
        label: 'Ignorar',
        color: 'neutral',
        variant: 'subtle',
        onClick: (e: MouseEvent) => e?.stopPropagation()
      }
    ]
  })
}

const orientations = [
  { value: 'vertical', note: 'Los botones caen bajo el texto.' },
  { value: 'horizontal', note: 'Los botones van alineados con la X.' }
] as const

function showOrientation(orientation: 'vertical' | 'horizontal') {
  toast.add({
    title: 'Elemento eliminado',
    description: 'Se puede deshacer durante 10 segundos.',
    icon: 'i-lucide-trash-2',
    duration: 8000,
    orientation,
    actions: [
      { label: 'Deshacer', color: 'neutral', variant: 'outline', onClick: (e: MouseEvent) => e?.stopPropagation() },
      { label: 'Cerrar', color: 'neutral', variant: 'ghost', onClick: (e: MouseEvent) => e?.stopPropagation() }
    ]
  })
}

// --------------------------------------------------------------------------
// DEMO 8: el toast entero es clicable
//
// onClick a nivel de toast pone el cursor de mano sobre la tarjeta. Es el
// patrón del aviso que lleva a algún sitio: pincha y navega.
function showClickable() {
  toast.add({
    title: 'La factura está lista',
    description: 'Pincha aquí para abrirla.',
    icon: 'i-lucide-file-down',
    color: 'primary',
    duration: 0,
    onClick: () => {
      toast.add({
        title: 'Aquí iría la factura',
        description: 'Este proyecto no tiene nada que abrir todavía.',
        icon: 'i-lucide-info',
        color: 'neutral'
      })
    }
  })
}

// --------------------------------------------------------------------------
// DEMO 9: el mismo id no duplica, pulsa
//
// add() genera un id si no le pasas uno, así que dos llamadas seguidas crean
// dos toasts. Con id fijo, la segunda fusiona en el primero y lo marca para
// que pulse. Es lo que evita la avalancha de avisos cuando el usuario
// pulsa "copiar" quince veces.
function showCopy() {
  toast.add({
    id: 'clipboard',
    title: 'Copiado al portapapeles',
    icon: 'i-lucide-clipboard-check',
    color: 'success',
    duration: 2000
  })
}

const duplicateStats = computed(() => ({
  total: toasts.value.length,
  pinned: toasts.value.filter(t => t.id === 'clipboard').length
}))

// --------------------------------------------------------------------------
// DEMO 10: remove, clear y el array en vivo
//
// toasts es un Ref con la cola entera, incluidos los que se están
// cerrando. Sirve para un panel de notificaciones propio.
function titleOf(item: Toast) {
  return typeof item.title === 'string' ? item.title : '(nodo)'
}

function removeOne(id?: string | number) {
  if (id === undefined) {
    return
  }

  toast.remove(id)
}

function removeAllAnimated() {
  const pending = [...toasts.value]

  pending.forEach((item, index) => {
    setTimeout(() => toast.remove(item.id), index * 120)
  })
}

// --------------------------------------------------------------------------
// DEMO 11: contenido con h()
//
// title y description aceptan un VNode, no solo texto. Con h() se puede
// meter HTML o un componente de Nuxt UI dentro del aviso, con sus clases
// y su token. Ojo: el toast es JSON cuando viene del servidor, así que un
// VNode solo es válido si el aviso nace en el cliente.
function showHtml() {
  toast.add({
    title: h('span', {}, [
      'El elemento ',
      h('span', { class: 'text-primary font-bold' }, '#15'),
      ' se ha borrado'
    ]),
    description: h('span', {}, [
      'Se puede deshacer desde ',
      h('span', { class: 'font-bold' }, 'Historial'),
      '.'
    ]),
    icon: 'i-lucide-trash-2',
    color: 'warning',
    duration: 6000
  })
}

// --------------------------------------------------------------------------
// DEMO 12: type, cómo lo anuncia el lector de pantalla
//
// foreground interrumpe lo que el usuario esté leyendo. background
// espera a que termine. Los avisos que dispara una acción suya son
// foreground; los que nacen solos (sincronización, un proceso que acaba)
// son background.
const a11yTypes = [
  { value: 'foreground', label: 'foreground', note: 'El usuario acaba de pedirlo. Interrumpe.' },
  { value: 'background', label: 'background', note: 'Ha pasado solo. Espera su turno.' }
] as const

function showType(type: 'foreground' | 'background') {
  toast.add({
    title: type === 'foreground' ? 'Enlace copiado' : 'Sincronización terminada',
    description: type === 'foreground'
      ? 'Como tú lo pediste, se anuncia ya.'
      : 'Nadie lo ha pedido: espera a que el lector pare.',
    icon: type === 'foreground' ? 'i-lucide-clipboard-check' : 'i-lucide-cloud-upload',
    color: type === 'foreground' ? 'success' : 'neutral',
    type,
    duration: 5000
  })
}

// --------------------------------------------------------------------------
// DEMO 13: la configuración del toaster, en vivo
//
// Todo lo de aquí son props del <UToaster>, que <UApp> reenvía desde su
// prop `toaster`. Los valores salen de useToasterOptions(), el estado
// compartido que app.vue pasa a <UApp>.
//
// position decide también el gesto: en top-* el aviso se cierra arrastrando
// hacia arriba, en bottom-* hacia abajo, y en left/right hacia el borde.
// disableSwipe quita ese gesto; swipeThreshold cambia cuánta distancia hace
// falta, en píxeles.
const positions = [
  'top-left', 'top-center', 'top-right',
  'bottom-left', 'bottom-center', 'bottom-right'
] as const

function showProbe() {
  toast.add({
    title: `Posición: ${options.value.position}`,
    description: 'Arrástrame para cerrar.',
    icon: 'i-lucide-mouse-pointer-click',
    color: 'primary',
    duration: 6000
  })
}

const maxOptions = [1, 3, 5, 8] as const

function flood() {
  for (let i = 1; i <= 8; i++) {
    toast.add({
      title: `Aviso ${i}`,
      description: 'Ocho seguidos, para ver qué hace max.',
      icon: 'i-lucide-bell',
      color: 'neutral',
      duration: 8000,
      progress: false
    })
  }
}

// --------------------------------------------------------------------------
// DEMO 14: el patrón de verdad, un envío con estado
//
// Aquí está todo junto, que es como se usa: se lanza en falso, se espera,
// y el resultado se pinta sea cual sea. Un formulario que falla tiene que
// decirlo aunque el error se haya dado después de cerrar el modal, y eso
// es justo lo que un toast resuelve y un UAlert dentro del formulario no.
const form = reactive({
  email: '',
  state: 'idle' as 'idle' | 'sending' | 'error'
})

// En cuanto el usuario toca el campo, el error se va. Dejarlo pegado
// mientras escribe es el gesto que hace que un formulario parezca roto:
// el mensaje sigue ahí mientras el texto ya es válido.
watch(() => form.email, () => {
  if (form.state === 'error') {
    form.state = 'idle'
  }
})

async function submitForm() {
  if (form.state === 'sending') {
    return
  }

  form.state = 'sending'

  await new Promise(resolve => setTimeout(resolve, 1200))

  if (!form.email.includes('@')) {
    form.state = 'error'

    toast.add({
      title: 'Ese correo no vale',
      description: 'Revisa que tenga arroba y dominio.',
      icon: 'i-lucide-circle-x',
      color: 'error',
      duration: 5000
    })

    return
  }

  form.state = 'idle'

  toast.add({
    title: 'Te has dado de alta',
    description: `Enviaremos la confirmación a ${form.email}.`,
    icon: 'i-lucide-circle-check',
    color: 'success',
    actions: [
      {
        label: 'Deshacer',
        color: 'neutral',
        variant: 'outline',
        onClick: (e: MouseEvent) => {
          e?.stopPropagation()

          toast.add({
            title: 'Alta cancelada',
            description: form.email,
            icon: 'i-lucide-undo-2',
            color: 'neutral'
          })
        }
      }
    ]
  })
}

// --------------------------------------------------------------------------
// DEMO 15: toast o UAlert
//
// Las dos cajas comparten casi todos los campos: title, description, icon,
// avatar, color, actions, orientation, close, closeIcon. La diferencia es
// de sitio, no de contenido.
// Cada UAlert trae su propio texto porque los cuatro juntos se leen como
// una galeria: si todos dicen lo mismo, solo se ve el color y no el peso
// que aporta cada variante.
const alertVariants = [
  { variant: 'solid', note: 'El que más pesa. Fondo relleno y texto invertido.' },
  { variant: 'subtle', note: 'Fondo suave más un anillo. El aviso de sistema.' },
  { variant: 'soft', note: 'Solo fondo suave, sin anillo. El más discreto.' },
  { variant: 'outline', note: 'Solo anillo. Para cuando el texto manda, no el color.' }
] as const

const decision = [
  { when: 'El resultado de una acción', with: 'toast', why: 'Ya pasó. Se va solo y no empuja el contenido.' },
  { when: 'Algo que hay que leer', with: 'UAlert', why: 'Se va con el scroll, como el resto del texto.' },
  { when: 'Un error de campo', with: 'UAlert', why: 'Tiene que estar junto al campo, no en una esquina.' },
  { when: 'Un error de servidor', with: 'toast', why: 'Se sabe tras cerrar el formulario: sobrevive al cambio de vista.' },
  { when: 'Un aviso de cookies', with: 'UAlert', why: 'Es una decisión, no un evento. No puede expirar.' }
] as const
</script>

<template>
  <UPageSection
    id="toasts"
    title="Toasts y avisos"
    description="Los avisos de Nuxt UI son dos piezas distintas con los mismos campos. Un toast es el resultado de algo que ya pasó y vive fuera del flujo; un UAlert es una caja en la página que el visitante debería leer aunque no pulse nada."
    :ui="ui"
  >
    <div class="flex flex-col gap-14">
      <!-- DEMO 1 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El aviso mínimo
          </h3>

          <code class="text-toned text-xs">toast.add({ title, description })</code>
        </div>

        <p class="text-muted text-sm">
          Todo sale de <code class="text-toned">useToast()</code>, que devuelve
          <code class="text-toned">add</code>, <code class="text-toned">update</code>,
          <code class="text-toned">remove</code>, <code class="text-toned">clear</code> y
          <code class="text-toned">toasts</code>. No hay que importar nada ni registrar
          nada: sale del propio módulo. Y no hay que pasarle las props al
          <code class="text-toned">Toaster</code> para que se vea.
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <UButton
            label="Lanzar un toast"
            icon="i-lucide-bell"
            size="sm"
            @click="showMinimal"
          />

          <div class="flex flex-col gap-1.5">
            <div
              v-for="field in anatomy"
              :key="field.field"
              class="flex flex-wrap items-baseline gap-2 text-xs"
            >
              <code class="text-highlighted font-semibold">{{ field.field }}</code>

              <code class="text-dimmed">{{ field.type }}</code>

              <span class="text-muted">{{ field.note }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- DEMO 2 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Color
        </h3>

        <p class="text-muted text-sm">
          <code class="text-toned">color</code> acepta los siete colores de Nuxt UI y
          cambia a la vez el icono, el anillo de la tarjeta y la barra de tiempo.
          Los cuatro semánticos son los que se usan de verdad;
          <code class="text-toned">secondary</code> y <code class="text-toned">neutral</code>
          están para aplicaciones con dos tonos de marca o para avisos sin matiz.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            v-for="color in colors"
            :key="color.value"
            :label="color.label"
            :icon="color.icon"
            :color="color.value"
            size="sm"
            variant="outline"
            @click="showColor(color)"
          />
        </div>
      </div>

      <!-- DEMO 3 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Qué va a la izquierda
        </h3>

        <p class="text-muted text-sm">
          <code class="text-toned">icon</code> y <code class="text-toned">avatar</code>
          compiten por el mismo hueco, y en ese orden: si los dos están, gana el avatar.
          El icono es el estado; el avatar es la persona. Y el botón de cerrar se
          personaliza con <code class="text-toned">closeIcon</code> o se quita entero
          con <code class="text-toned">close: false</code>, que es lo que hay que hacer
          cuando el aviso es crítico.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Con icono"
            icon="i-lucide-cloud-upload"
            size="sm"
            @click="showIcon"
          />

          <UButton
            label="Con avatar"
            icon="i-lucide-user-plus"
            size="sm"
            color="neutral"
            variant="outline"
            @click="showAvatar"
          />

          <UButton
            label="Sin nada"
            size="sm"
            color="neutral"
            variant="outline"
            @click="showPlain"
          />

          <UButton
            label="Otra X"
            icon="i-lucide-arrow-right"
            size="sm"
            color="neutral"
            variant="outline"
            @click="showCustomClose"
          />

          <UButton
            label="Sin cerrar"
            icon="i-lucide-lock"
            size="sm"
            color="error"
            variant="subtle"
            @click="showNoClose"
          />
        </div>
      </div>

      <!-- DEMO 4 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Cuánto vive y si lleva barra
        </h3>

        <p class="text-muted text-sm">
          <code class="text-toned">duration</code> va en milisegundos y
          <code class="text-toned">0</code> significa «no se cierra solo». La barra
          muestra el tiempo que queda, así que duplica lo que el aviso dice. La barra
          se pausa con el raton encima, pero el aviso no: el contador sigue corriendo.
          Para un error que hay que leer de verdad, mejor quitar la barra que alargarla.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            v-for="item in durations"
            :key="item.value"
            :label="item.label"
            :title="item.note"
            size="sm"
            variant="outline"
            @click="showDuration(item.value)"
          />

          <UButton
            label="Sin barra"
            icon="i-lucide-gauge"
            size="sm"
            color="error"
            variant="subtle"
            @click="showNoProgress"
          />

          <UButton
            label="Barra de otro color"
            icon="i-lucide-palette"
            size="sm"
            color="neutral"
            variant="subtle"
            @click="showForeignProgress"
          />
        </div>
      </div>

      <!-- DEMO 5 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El mismo toast cambiando de contenido
          </h3>

          <UBadge
            label="update"
            size="sm"
            color="primary"
            variant="subtle"
          />
        </div>

        <p class="text-muted text-sm">
          <code class="text-toned">add</code> devuelve el toast ya creado, y
          <code class="text-toned">update(id, ...)</code> lo reescribe en su sitio. Es el
          patrón de todo lo que tarda: se lanza con <code class="text-toned">duration: 0</code>
          para que no se cierre a medio camino y se va llamando a
          <code class="text-toned">update</code> con el progreso. El aviso no desaparece ni
          parpadea, se transforma, y el usuario no pierde de vista lo que estaba pasando.
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <UButton
              label="Subir un archivo"
              icon="i-lucide-cloud-upload"
              size="sm"
              :loading="uploadToast !== undefined"
              @click="startUpload"
            />

            <p class="text-toned text-xs">
              progreso local:
              <span class="text-highlighted font-mono tabular-nums">{{ Math.round(progress) }} %</span>
            </p>
          </div>

          <UProgress
            :model-value="progress"
            status
            size="sm"
          />
        </div>
      </div>

      <!-- DEMO 6 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El aviso que se convierte en su propio resultado
          </h3>

          <code class="text-toned text-xs">onUpdate:open</code>
        </div>

        <p class="text-muted text-sm">
          El callback se dispara cuando el estado <code class="text-toned">open</code>
          cambia, y eso incluye el cierre por tiempo. Se devuelve temprano si
          <code class="text-toned">open</code> es <code class="text-toned">true</code>:
          sin ese <code class="text-toned">return</code> el toast vuelve a disparar el
          callback al actualizarse, se actualiza otra vez, y se monta un bucle. Por eso
          el <code class="text-toned">update</code> final deja el callback en
          <code class="text-toned">undefined</code>.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Enviar formulario"
            icon="i-lucide-send"
            size="sm"
            @click="showCallback"
          />
        </div>
      </div>

      <!-- DEMO 7 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Acciones
        </h3>

        <p class="text-muted text-sm">
          <code class="text-toned">actions</code> es una lista de props de
          <code class="text-toned">UButton</code>, y por eso trae
          <code class="text-toned">label</code>, <code class="text-toned">icon</code>,
          <code class="text-toned">color</code>, <code class="text-toned">variant</code> y
          <code class="text-toned">onClick</code>. El <code class="text-toned">onClick</code>
          de una acción se dispara, y el del toast entero también: si ambos existen hay que
          llamar a <code class="text-toned">e.stopPropagation()</code> en el de la acción.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Con acciones"
            icon="i-lucide-wifi-off"
            size="sm"
            color="error"
            variant="subtle"
            @click="showActions"
          />

          <UButton
            v-for="item in orientations"
            :key="item.value"
            :label="item.value"
            :title="item.note"
            icon="i-lucide-trash-2"
            size="sm"
            color="neutral"
            variant="outline"
            @click="showOrientation(item.value)"
          />
        </div>
      </div>

      <!-- DEMO 8 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El aviso entero es clicable
          </h3>

          <code class="text-toned text-xs">onClick</code>
        </div>

        <p class="text-muted text-sm">
          Un <code class="text-toned">onClick</code> a nivel de toast pone el cursor de mano
          sobre la tarjeta y hace que el aviso completo sea un enlace. Es el patrón del
          aviso que lleva a alguna parte: una factura, una mención, una invitación. Si el
          aviso no lleva a ninguna parte, que no sea clicable: un cursor de mano que no
          hace nada es peor que no señalarlo.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Abrir uno clicable"
            icon="i-lucide-mouse-pointer-click"
            size="sm"
            @click="showClickable"
          />
        </div>
      </div>

      <!-- DEMO 9 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            El mismo id no duplica: pulsa
          </h3>

          <UBadge
            :label="`${duplicateStats.total} en la cola`"
            size="sm"
            color="neutral"
            variant="subtle"
          />
        </div>

        <p class="text-muted text-sm">
          <code class="text-toned">add</code> genera un id si no le pasas uno, así que dos
          llamadas seguidas crean dos toasts. Con <code class="text-toned">id</code> fijo, la
          segunda <em>fusiona</em> en el primero y lo marca para que pulse en vez de
          duplicarse. Es lo que evita la avalancha cuando alguien pulsa «copiar» quince
          veces: sigue habiendo
          <span class="text-highlighted font-mono">{{ duplicateStats.pinned }}</span>
          aviso con ese id, no quince.
        </p>

        <div class="bg-muted flex flex-wrap items-center gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Copiar"
            icon="i-lucide-copy"
            size="sm"
            @click="showCopy"
          />

          <code class="text-dimmed text-xs">id: 'clipboard'</code>
        </div>
      </div>

      <!-- DEMO 10 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            La cola, a la vista
          </h3>

          <div class="flex gap-2">
            <UButton
              label="Quitar uno"
              icon="i-lucide-minus"
              size="sm"
              color="neutral"
              variant="outline"
              @click="removeOne(toasts[0]?.id)"
            />

            <UButton
              label="Vaciar"
              icon="i-lucide-x"
              size="sm"
              color="neutral"
              variant="subtle"
              @click="toast.clear()"
            />
          </div>
        </div>

        <p class="text-muted text-sm">
          <code class="text-toned">toasts</code> es un <code class="text-toned">Ref</code> con
          la cola entera, así que un panel de notificaciones propio sale de ahí sin
          ningún esfuerzo. Fíjate en la diferencia de los dos botones:
          <code class="text-toned">remove(id)</code> marca el aviso como cerrado y lo saca
          del array 200 ms después, que es lo que le da tiempo a deslizarse;
          <code class="text-toned">clear()</code> lo vacía de golpe y no anima nada. Si el
          efecto importa, quita uno a uno con un retardo entre llamadas.
        </p>

        <div class="bg-muted flex flex-col gap-3 rounded-xl border border-default p-5">
          <div
            v-if="!toasts.length"
            class="text-dimmed py-4 text-center text-sm"
          >
            La cola está vacía. Lanza algo desde arriba.
          </div>

          <div
            v-for="item in toasts"
            :key="item.id"
            class="bg-default flex flex-wrap items-center gap-3 rounded-lg border border-default px-3 py-2 text-sm"
          >
            <code class="text-dimmed w-24 shrink-0 truncate text-xs">{{ item.id }}</code>

            <span class="text-highlighted min-w-0 flex-1 truncate">{{ titleOf(item) }}</span>

            <UBadge
              v-if="item.color"
              :label="item.color"
              :color="item.color"
              size="sm"
              variant="subtle"
            />

            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="xs"
              aria-label="Quitar"
              @click="removeOne(item.id)"
            />
          </div>

          <UButton
            label="Vaciar uno a uno, con retardo"
            icon="i-lucide-waves"
            size="sm"
            color="neutral"
            variant="outline"
            block
            @click="removeAllAnimated"
          />
        </div>
      </div>

      <!-- DEMO 11 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            Contenido con <code class="text-toned font-mono">h()</code>
          </h3>

          <code class="text-toned text-xs">title: h('span', {}, [...])</code>
        </div>

        <p class="text-muted text-sm">
          <code class="text-toned">title</code> y
          <code class="text-toned">description</code> aceptan un <code class="text-toned">VNode</code>,
          no solo texto. Con <code class="text-toned">h()</code> se puede meter HTML o un
          componente de Nuxt UI dentro del aviso, con sus clases y sus tokens. El límite es
          que el estado de la cola es serializable: un VNode solo vale si el aviso nace en el
          cliente. Un <code class="text-toned">UAlert</code> en la página no tiene ese
          problema.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            label="Con HTML dentro"
            icon="i-lucide-type"
            size="sm"
            @click="showHtml"
          />
        </div>
      </div>

      <!-- DEMO 12 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            Cómo lo anuncia el lector de pantalla
          </h3>

          <code class="text-toned text-xs">type: 'foreground' | 'background'</code>
        </div>

        <p class="text-muted text-sm">
          Es el único campo del aviso que no se ve. <code class="text-toned">foreground</code>
          interrumpe lo que el lector de pantalla esté leyendo;
          <code class="text-toned">background</code> espera a que termine. El criterio es la
          causa, no la gravedad: si el aviso nace de una acción que el usuario acaba de
          hacer, es <code class="text-toned">foreground</code> aunque sea un
          <code class="text-toned">error</code>, porque para él es la respuesta que espera.
          Y al revés: un aviso de fondo con <code class="text-toned">foreground</code> corta
          justo lo que el usuario estaba leyendo para decirle algo que no había pedido.
        </p>

        <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
          <UButton
            v-for="item in a11yTypes"
            :key="item.value"
            :label="item.label"
            :title="item.note"
            size="sm"
            color="neutral"
            variant="outline"
            @click="showType(item.value)"
          />
        </div>
      </div>

      <!-- DEMO 13 -->
      <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h3 class="text-highlighted text-lg font-semibold">
            La configuración del toaster
          </h3>

          <code class="text-toned text-xs">:toaster="toaster" en &lt;UApp&gt;</code>
        </div>

        <p class="text-muted text-sm">
          Todo lo de este bloque son props del <code class="text-toned">Toaster</code>, que
          <code class="text-toned">UApp</code> reenvía desde su prop
          <code class="text-toned">toaster</code>. En <code class="text-toned">app.vue</code>
          está atado a un estado compartido para poder cambiarlo en caliente; si no lo
          necesitas, bórralo y deja que todo use los valores por defecto. Los botones de aquí
          escriben ese estado, y los toasts que ya están en pantalla no se mueven: los
          ajustes se aplican a los que vengan después.
        </p>

        <div class="bg-muted flex flex-col gap-5 rounded-xl border border-default p-5">
          <div class="flex flex-col gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <UIcon
                name="i-lucide-map-pin"
                class="text-primary size-4 shrink-0"
              />

              <span class="text-sm font-medium">position</span>
            </div>

            <div class="flex flex-wrap gap-2">
              <UButton
                v-for="position in positions"
                :key="position"
                :label="position"
                size="xs"
                :variant="options.position === position ? 'solid' : 'subtle'"
                :color="options.position === position ? 'primary' : 'neutral'"
                @click="options.position = position"
              />
            </div>

            <p class="text-dimmed text-xs">
              top-right · top-center · top-left · bottom-right · bottom-center · bottom-left
            </p>
          </div>

          <div class="flex flex-col gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <UIcon
                name="i-lucide-layers"
                class="text-primary size-4 shrink-0"
              />

              <span class="text-sm font-medium">max</span>
            </div>

            <div class="flex flex-wrap gap-2">
              <UButton
                v-for="value in maxOptions"
                :key="value"
                :label="String(value)"
                size="xs"
                :variant="options.max === value ? 'solid' : 'subtle'"
                :color="options.max === value ? 'primary' : 'neutral'"
                @click="options.max = value"
              />

              <UButton
                label="Lanzar 8"
                icon="i-lucide-bell"
                size="xs"
                variant="outline"
                @click="flood"
              />
            </div>

            <p class="text-dimmed text-xs">
              Al pasarse, el aviso más antiguo desaparece del estado sin animación de salida.
              Ponlo a 1 y lanza ocho: se ve mejor que explicarlo.
            </p>
          </div>

          <div class="flex flex-col gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <UIcon
                name="i-lucide-timer"
                class="text-primary size-4 shrink-0"
              />

              <span class="text-sm font-medium">duration</span>
            </div>

            <div class="flex flex-wrap items-center gap-3">
              <UButton
                v-for="value in [1500, 5000, 10000]"
                :key="value"
                :label="`${value / 1000} s`"
                size="xs"
                :variant="options.duration === value ? 'solid' : 'subtle'"
                :color="options.duration === value ? 'primary' : 'neutral'"
                @click="options.duration = value"
              />

              <UButton
                label="Probar"
                icon="i-lucide-play"
                size="xs"
                variant="outline"
                @click="showProbe"
              />
            </div>

            <p class="text-dimmed text-xs">
              Valor por defecto para los toasts que no traigan su propio
              <code>duration</code>.
            </p>
          </div>

          <div class="flex flex-wrap items-center gap-4">
            <USwitch
              v-model="options.expand"
              label="expand"
              description="Apila los toasts detrás del último. El raton por encima los despliega y pausa los temporizadores."
            />

            <USwitch
              v-model="options.progress"
              label="progress"
              description="Quita la barra de tiempo de todos los toasts."
            />
          </div>

          <div class="bg-default flex flex-col gap-2 rounded-lg border border-dashed border-accented p-4">
            <div class="flex items-center gap-2">
              <UIcon
                name="i-lucide-mouse-pointer-click"
                class="text-primary size-4 shrink-0"
              />

              <span class="text-sm font-medium">El gesto de cerrar</span>
            </div>

            <p class="text-muted text-xs">
              El toaster deduce el sentido del gesto de la posición: en
              <code class="text-toned">top-*</code> se arrastra hacia arriba, en
              <code class="text-toned">bottom-*</code> hacia abajo y en los lados hacia el
              borde. Cambia la posición de arriba, suelta el aviso de «Probar» y ciérralo arrastrándolo
              hacia el borde.
              el sentido cambia con ella.
              <code class="text-toned">disableSwipe</code> quita el gesto y
              <code class="text-toned">swipeThreshold</code> cambia cuántos píxeles hace
              falta para que cuente.
            </p>
          </div>
        </div>
      </div>

      <!-- DEMO 14 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          El patrón de verdad
        </h3>

        <p class="text-muted text-sm">
          Todo lo anterior junta, que es como se usa: se lanza en falso, se espera, y se
          pinta el resultado sea cual sea. Un formulario que falla tiene que decirlo aunque
          el error aparezca después de cerrar el formulario, y eso es justo lo que resuelve
          un toast y no resuelve un <code class="text-toned">UAlert</code> dentro del
          formulario. Escribe un correo sin arroba.
        </p>

        <div class="bg-muted flex flex-col gap-4 rounded-xl border border-default p-5">
          <UFormField
            label="Correo"
            name="email"
            :error="form.state === 'error' ? 'Necesita una arroba y un dominio' : undefined"
          >
            <UInput
              v-model="form.email"
              placeholder="ana@ejemplo.com"
              icon="i-lucide-mail"
              autocomplete="off"
              class="w-full"
              :disabled="form.state === 'sending'"
            />
          </UFormField>

          <div class="flex flex-wrap items-center gap-2">
            <UButton
              label="Darme de alta"
              icon="i-lucide-user-plus"
              size="sm"
              :loading="form.state === 'sending'"
              @click="submitForm"
            />

            <code class="text-dimmed text-xs">
              {{ form.state === 'sending' ? 'enviando…' : 'idle' }}
            </code>
          </div>
        </div>
      </div>

      <!-- DEMO 15 -->
      <div class="flex flex-col gap-4">
        <h3 class="text-highlighted text-lg font-semibold">
          Cuál de los dos
        </h3>

        <p class="text-muted text-sm">
          Las dos cajas comparten casi todos los campos:
          <code class="text-toned">title</code>, <code class="text-toned">description</code>,
          <code class="text-toned">icon</code>, <code class="text-toned">avatar</code>,
          <code class="text-toned">color</code>, <code class="text-toned">actions</code>,
          <code class="text-toned">orientation</code>, <code class="text-toned">close</code> y
          <code class="text-toned">closeIcon</code>. Lo único que de verdad distingue a una
          de la otra es el sitio, y <code class="text-toned">variant</code>, que el toast no
          tiene: el toast es siempre una tarjeta con anillo.
        </p>

        <div class="flex flex-col gap-4">
          <div class="bg-muted flex flex-col gap-3 rounded-xl border border-default p-5">
            <div class="flex flex-wrap items-center gap-2">
              <UIcon
                name="i-lucide-square-stack"
                class="text-primary size-4 shrink-0"
              />

              <code class="text-sm font-semibold">UAlert</code>

              <code class="text-dimmed text-xs">en la página, con variante</code>
            </div>

            <UAlert
              v-for="item in alertVariants"
              :key="item.variant"
              color="warning"
              :variant="item.variant"
              icon="i-lucide-triangle-alert"
              title="Los avisos de cookies no caducan"
              :description="item.note"
              :actions="[{ label: 'Aceptar', color: 'warning', variant: 'outline' }, { label: 'Rechazar', color: 'neutral', variant: 'ghost' }]"
            />
          </div>

          <div class="overflow-x-auto rounded-xl border border-default">
            <table class="w-full min-w-150 border-collapse text-left text-sm">
              <thead>
                <tr class="bg-muted text-dimmed text-xs">
                  <th class="px-4 py-3 font-medium">
                    Situación
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Herramienta
                  </th>
                  <th class="px-4 py-3 font-medium">
                    Por qué
                  </th>
                </tr>
              </thead>

              <tbody>
                <tr
                  v-for="row in decision"
                  :key="row.when"
                  class="border-default border-t"
                >
                  <td class="text-default px-4 py-3 align-top">
                    {{ row.when }}
                  </td>

                  <td class="px-4 py-3 align-top">
                    <code
                      class="text-xs font-semibold"
                      :class="row.with === 'toast' ? 'text-primary' : 'text-toned'"
                    >
                      {{ row.with }}
                    </code>
                  </td>

                  <td class="text-muted px-4 py-3 align-top">
                    {{ row.why }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
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
            Lo que esta sección no cambia
          </h3>
        </div>

        <p class="text-muted text-sm">
          Toaster y alertas no pasan por
          <code class="text-toned">RevealOnScroll</code>, y no por casualidad: el contenedor
          se monta en el <code class="text-toned">body</code> mediante un portal. Los avisos
          aparecen por encima de cualquier sección, y no se van cuando el bloque que los
          lanzó sale de pantalla. Un toast dentro de un
          <code class="text-toned">RevealOnScroll</code> con <code class="text-toned">blur</code>
          seguiría funcionando por ese motivo, porque el portal lo saca del contexto de
          apilamiento que crea el filtro.
        </p>

        <p class="text-muted text-sm">
          Y al revés: como el toaster vive fuera del flujo, un toast no se lleva por
          delante el contenido ni empuja el scroll. Si el aviso necesita ocupar sitio, o
          que el usuario pueda volver a él más tarde, no es un toast: es una fila en una
          página de notificaciones.
        </p>

        <p class="text-muted text-sm">
          La única pieza de esta página que sí hay que tocar para usar los toasts es
          <code class="text-toned">app.vue</code>, y solo por el binding opcional de
          <code class="text-toned">:toaster</code>. El
          <code class="text-toned">&lt;UApp&gt;</code> sin más ya los pinta.
        </p>
      </div>
    </div>
  </UPageSection>
</template>
