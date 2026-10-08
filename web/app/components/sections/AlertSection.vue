<script setup lang="ts">
// ======================================================================
// ALERTAS Y AVISOS PROGRAMATICOS POR DEMOSTRACION
// La parte nueva de esta seccion es useAppNotify, en utils/appNotify.ts.
// UAlert ya se usaba en la seccion de modales.
//
// Lo que se ve aqui:
//
//   1. useAppNotify: `notify('texto')` y `notify('texto', 'error')`. El
//      color solo se escribe cuando no es primary, y el icono sale solo.
//
//   2. Los siete colores de UAlert, uno de cada uno. Los mismos que
//      acepta el toast, porque los dos leen el mismo tema.
//
//   3. Los cuatro variants, que es la confusion clasica: soft y subtle
//      parecen lo mismo y no lo son.
//
//   4. Los dos usos de UAlert segun cuando conviene cerrarlo.
//
// --------------------------------------------------------------------------
// UALERT O TOAST: NO ES CUESTION DE GUSTO
//
// UAlert vive EN PAGINA. Empuja el contenido, se va con el scroll y
// permanece mientras haya algo que leer. Es lo que se usa para lo que el
// usuario DEBERIA leer: un error de validacion pegado al campo que
// fallo, un aviso de mantenimiento, un banner de cookies.
//
// useToast() es una COLA GLOBAL. Se monta en el <body>, sobrevive a la
// navegacion y se va solo a los 5 segundos. Es lo que se usa para el
// resultado de una ACCION: "guardado", "hemos recibido tu mensaje", "la
// sesion ha caducado". Lo que paso ya, que no se quede ahi.
//
// El error clasico es ponerlos al reves. Un error de validacion como
// toast se va solo a los 5 segundos y el usuario no ha vuelto a mirar; un
// "guardado" como alert empuja la pagina y se va con el scroll.
//
// OJO: el toast NO necesita que montes nada. <UApp> en app/app.vue ya
// monta el <UToaster> que lo dibuja. Si no estuviera, los avisos se
// encolarian y no apareceria nada en pantalla, sin error ni aviso en
// consola. Ese es el fallo mas caro de esta API, y en este proyecto ya
// esta resuelto.
const { notify } = useAppNotify()

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

// Los siete que acepta el tema. Van en .nuxt/ui/alert.ts.
const colors = [
  { color: 'primary', label: 'primary' },
  { color: 'secondary', label: 'secondary' },
  { color: 'success', label: 'success' },
  { color: 'info', label: 'info' },
  { color: 'warning', label: 'warning' },
  { color: 'error', label: 'error' },
  { color: 'neutral', label: 'neutral' }
] as const

// Los cuatro. Se pareceran en pantalla, y aun asi no son lo mismo.
const variants = [
  {
    variant: 'solid',
    label: 'solid',
    note: 'Relleno con el color a tope. Para lo que hay que notar ya.'
  },
  {
    variant: 'soft',
    label: 'soft',
    note: 'Fondo tenue del color, icono y texto a color. El mas legible de largo.'
  },
  {
    variant: 'subtle',
    label: 'subtle',
    note: 'Fondo casi neutro y solo el icono a color. Para no ensuciar la pagina.'
  },
  {
    variant: 'outline',
    label: 'outline',
    note: 'Solo borde. El mas discreto; el texto manda.'
  }
] as const

// El texto va en el script, no en el atributo: una llamada a funcion con
// llaves dentro de un binding del template ({ title: 'x' }) la confunde al
// parser de Vue, que ve el { como el principio de otra expresion.
const verbaje = 'notify(\'Guardado\') escribe title, color e icon. '
  + 'toast.add({ title: \'Guardado\', color: \'success\', icon: \'i-lucide-circle-check\' }) '
  + 'escribe lo mismo de largo. El atajo deduce el icon del color.'

// Un alert cerrable con onClose: es lo que se usa cuando el aviso es
// opcional y estorba.
const isVisible = ref(true)

// Un formulario con un error pegado al campo. El caso donde el toast es
// claramente la eleccion equivocada.
const email = ref('')
const emailError = computed(() => email.value.length === 0 || !email.value.includes('@')
  ? 'Escribe un correo válido.'
  : ''
)
</script>

<template>
  <UPageSection
    id="avisos"
    title="Alertas y avisos"
    description="Dos piezas que se confunden. UAlert es una caja en la página, para lo que hay que leer. useToast() es una cola global, para el resultado de una acción. useAppNotify es el atajo para el segundo."
    :ui="ui"
  >
    <div class="flex flex-col gap-10">
      <div class="bg-muted flex flex-wrap items-center gap-2 rounded-xl border border-default p-5">
        <UButton
          label="notify('Guardado')"
          icon="i-lucide-bell"
          size="sm"
          @click="notify('Guardado')"
        />

        <UButton
          label="…'success'"
          size="sm"
          color="success"
          variant="subtle"
          @click="notify('Cambios guardados', 'success')"
        />

        <UButton
          label="…'warning'"
          size="sm"
          color="warning"
          variant="subtle"
          @click="notify('La sesión caduca en 5 minutos', 'warning')"
        />

        <UButton
          label="…'error'"
          size="sm"
          color="error"
          variant="subtle"
          @click="notify('No se pudo guardar', 'error')"
        />
      </div>

      <div class="bg-elevated flex flex-col gap-6 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            Un shortcut y el objeto entero
          </h3>

          <p class="text-muted text-sm">
            <code class="text-primary">notify()</code> cubre los casos
            frecuentes: mensaje y color. Cuando hace falta más, se usa
            <code class="text-primary">toast.add()</code> directamente, que
            acepta icon, description, duration o actions.
          </p>
        </div>

        <UAlert
          color="neutral"
          variant="subtle"
          icon="i-lucide-info"
          title="Mismo resultado, distinto verbaje"
          :description="verbaje"
        />
      </div>

      <div class="flex flex-col gap-3">
        <h3 class="text-highlighted font-semibold">
          Los siete colores
        </h3>

        <div class="flex flex-col gap-3">
          <UAlert
            v-for="item in colors"
            :key="item.color"
            :color="item.color"
            variant="soft"
            :title="item.color"
            :icon="`i-lucide-circle-dot`"
            :description="`UAlert acepta ${item.color}. El mismo color vale para useToast y para notify().`"
          />
        </div>
      </div>

      <div class="flex flex-col gap-3">
        <h3 class="text-highlighted font-semibold">
          Los cuatro variants
        </h3>

        <div class="grid gap-4 sm:grid-cols-2">
          <div
            v-for="item in variants"
            :key="item.variant"
            class="flex flex-col gap-3"
          >
            <code class="text-dimmed text-xs">
              {{ item.label }}
            </code>

            <UAlert
              :variant="item.variant"
              color="warning"
              title="Sesión a punto de caducar"
              :description="item.note"
            />
          </div>
        </div>
      </div>

      <div class="flex flex-col gap-3">
        <h3 class="text-highlighted font-semibold">
          Cuándo cerrar el alert
        </h3>

        <p class="text-muted text-sm">
          Con <code class="text-primary">close</code> el usuario lo quita: es
          un aviso opcional que estorba. Sin él, el alert se queda para que
          nadie se lo pierda.
        </p>

        <UAlert
          v-if="isVisible"
          color="warning"
          variant="soft"
          icon="i-lucide-megaphone"
          title="Mantenimiento el domingo de 02:00 a 04:00"
          description="Se corta el servicio mientras actualizamos la base de datos."
          close
          @close="isVisible = false"
        />

        <UButton
          v-if="!isVisible"
          label="Volver a mostrar el aviso"
          size="sm"
          variant="subtle"
          icon="i-lucide-rotate-ccw"
          @click="isVisible = true"
        />
      </div>

      <div class="bg-elevated flex flex-col gap-4 rounded-xl border border-default p-6">
        <div class="flex flex-col gap-1">
          <h3 class="text-highlighted font-semibold">
            Por qué este error no puede ser un toast
          </h3>

          <p class="text-muted text-sm">
            Se queda hasta corregir el campo, y va pegado al campo. Como
            toast se iría a los cinco segundos y el usuario seguiría sin
            saber qué escribir.
          </p>
        </div>

        <UFormField
          label="Correo"
          name="email"
          required
          :error="emailError"
          :description="emailError ? '' : 'Sin publicidad ni nada. Salvo eso.'"
        >
          <UInput
            v-model="email"
            placeholder="tu@correo.com"
            icon="i-lucide-mail"
            autocomplete="email"
            class="w-full"
          />
        </UFormField>

        <div class="flex justify-end gap-2">
          <UButton
            label="Cancelar"
            color="neutral"
            variant="ghost"
          />

          <UButton
            label="Enviar"
            icon="i-lucide-send"
            :disabled="Boolean(emailError)"
          />
        </div>
      </div>
    </div>
  </UPageSection>
</template>
