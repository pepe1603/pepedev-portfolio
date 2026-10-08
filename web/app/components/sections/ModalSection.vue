<script setup lang="ts">
// ======================================================================
// AppModal POR DEMOSTRACION
// El modal reusable vive en components/AppModal.vue. Esta seccion solo
// existe para enseñarlo: tres formas de usarlo y por que.
//
// Lo que se ve aqui:
//
//   1. v-model:open con el estado en la pagina. El padre decide cuando
//      abrir; el modal no se abre solo.
//   2. Confirmar NO cierra el modal. La peticion va por dentro, se espera,
//      y si va bien se cierra desde aqui. El boton acepta confirmLoading
//      para que un doble clic no dispare dos veces la misma llamada.
//   3. Un caso destructivo, con confirmColor="error" y un boton de
//      cancelar que no es el de por defecto.
//
// Los dos campos del formulario hacen doble trabajo: son el contenido
// que hay que rellenar, y el motivo por el que confirmar empieza
// deshabilitado. Asi se ve la diferencia entre "no se puede confirmar" y
// "se esta confirmando": la primera es una condicion, la segunda un
// estado en vuelo.
const toast = useToast()

const ui = {
  container: 'py-24 sm:py-32 lg:py-40'
}

const rules = [
  {
    title: 'Confirmar no cierra',
    note: 'Quien cierra es el padre, y cuando le cuadra: si la petición va por dentro, cerrar en el clic deja al usuario mirando un formulario que se está guardando. Si la petición falla, además ya no hay nada que reintentar.'
  },
  {
    title: 'El estado vive fuera',
    note: 'v-model:open es un ref de la página, no del modal. Eso permite abrirlo desde un botón, cerrarlo desde un atajo de teclado o dejarlo abierto al cambiar de ruta, sin que el modal tenga que saber de dónde le viene.'
  },
  {
    title: 'El pie tiene un solo sitio',
    note: 'Los dos botones por defecto cubren casi todo. Cuando hace falta otra cosa se sustituye el slot #footer entero, como hace el primer modal de aquí, en vez de esconder botones con v-if y pelearse con el hueco que dejan.'
  }
] as const

const isOpen = ref(false)
const isDangerOpen = ref(false)

const projectName = ref('')
const environment = ref('production')
const confirmLabel = ref('')

const isSaving = ref(false)

const canConfirm = computed(() => confirmLabel.value.trim().length >= 3)

// Si el formulario va vacio, confirmar tiene que explicar por que esta
// apagado. No es solo un boton gris: el mensaje va con el, y desaparece
// en cuanto la condicion se cumple.
const confirmHint = computed(() => confirmLabel.value.trim().length >= 3
  ? ''
  : 'Escribe al menos tres caracteres para confirmar.'
)

async function save() {
  if (!canConfirm.value || isSaving.value) {
    return
  }

  isSaving.value = true

  // Sustituir por la peticion real. El awaited es lo que hace que
  // confirmLoading sirva de algo: mientras dura, el boton no acepta otro
  // clic.
  await new Promise(resolve => setTimeout(resolve, 1100))

  isSaving.value = false
  isOpen.value = false

  toast.add({
    title: `${projectName.value} guardado`,
    description: `Entorno: ${environment.value}.`,
    icon: 'i-lucide-circle-check',
    color: 'success'
  })

  // No se limpia a proposito: si el guardado falla en el futuro, el
  // formulario sigue ahi con lo escrito y solo hay que reintentar.
}

function deleteProject() {
  isDangerOpen.value = false

  toast.add({
    title: `${projectName.value || 'El proyecto'} eliminado`,
    description: 'Sin vuelta atrás. Por eso el botón era rojo.',
    icon: 'i-lucide-trash-2',
    color: 'error'
  })
}
</script>

<template>
  <UPageSection
    id="modales"
    title="Modales"
    description="Un modal reusable no es un UModal con dos botones: es el mismo esqueleto en toda la aplicación, con un solo sitio donde tocar el pie y un estado que el padre controla. AppModal.vue envuelve a UModal y añade el v-model, los emits y el pie."
    :ui="ui"
  >
    <div class="flex flex-col gap-10">
      <div class="bg-muted flex flex-wrap gap-2 rounded-xl border border-default p-5">
        <UButton
          label="Abrir modal"
          icon="i-lucide-maximize-2"
          size="sm"
          @click="isOpen = true"
        />

        <UButton
          label="Abrir uno destructivo"
          icon="i-lucide-triangle-alert"
          size="sm"
          color="error"
          variant="subtle"
          @click="isDangerOpen = true"
        />
      </div>

      <div class="bg-elevated flex flex-col gap-4 rounded-xl border border-default p-6">
        <div class="flex items-center gap-2">
          <UIcon
            name="i-lucide-info"
            class="text-primary size-4 shrink-0"
          />

          <h3 class="text-highlighted font-semibold">
            Tres reglas del envoltorio
          </h3>
        </div>

        <div class="flex flex-col gap-3">
          <div
            v-for="(item, index) in rules"
            :key="item.title"
            class="flex gap-3"
          >
            <code class="text-dimmed w-5 shrink-0 text-xs tabular-nums">{{ index + 1 }}</code>

            <div class="flex flex-col gap-1">
              <p class="text-default text-sm font-medium">
                {{ item.title }}
              </p>

              <p class="text-muted text-sm">
                {{ item.note }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <AppModal
      v-model:open="isOpen"
      title="Guardar el proyecto"
      description="El nombre aparece en la barra y en las URLs. Cambiarlo despues rompe los enlaces."
      :confirm-disabled="!canConfirm"
      :confirm-loading="isSaving"
      @confirm="save"
    >
      <template #body>
        <div class="flex flex-col gap-4">
          <UFormField
            label="Nombre"
            name="projectName"
            required
          >
            <UInput
              v-model="projectName"
              placeholder="mi-aplicacion"
              icon="i-lucide-folder"
              autocomplete="off"
              class="w-full"
            />
          </UFormField>

          <UFormField
            label="Entorno"
            name="environment"
            :description="confirmHint || 'Donde se va a desplegar primero.'"
          >
            <USelect
              v-model="environment"
              :items="[
                { label: 'Producción', value: 'production' },
                { label: 'Staging', value: 'staging' },
                { label: 'Local', value: 'local' }
              ]"
              class="w-full"
            />
          </UFormField>
        </div>
      </template>

      <template #footer>
        <div class="flex w-full flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p
            v-if="confirmHint"
            class="text-dimmed text-xs"
          >
            {{ confirmHint }}
          </p>

          <span v-else />

          <div class="flex justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="outline"
              @click="isOpen = false"
            />

            <UButton
              label="Guardar"
              icon="i-lucide-check"
              :loading="isSaving"
              :disabled="!canConfirm"
              @click="save"
            />
          </div>
        </div>
      </template>
    </AppModal>

    <AppModal
      v-model:open="isDangerOpen"
      title="Eliminar el proyecto"
      description="Se borran el despliegue, los dominios y los registros. No hay forma de recuperarlo."
      confirm-label="Eliminar"
      confirm-color="error"
      @confirm="deleteProject"
    >
      <template #body>
        <UAlert
          color="error"
          variant="subtle"
          icon="i-lucide-triangle-alert"
          title="Esta acción no se puede deshacer"
          description="Si solo querías pausar los despliegues, ciérralo y usa el botón de pausar."
          :actions="[{ label: 'Entendido', color: 'error', variant: 'outline' }]"
        />
      </template>
    </AppModal>
  </UPageSection>
</template>
