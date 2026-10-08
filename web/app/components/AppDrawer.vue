<script setup lang="ts">
// ======================================================================
// AppDrawer: un UDrawer con la envoltura de siempre.
//
// Que hace este componente y por que existe:
//
//   1. Traduce `side` a `direction`. UDrawer no llama asi a su prop:
//      se llama direction y acepta 'top' | 'bottom' | 'left' | 'right'.
//      Aqui la prop se llama side porque `side` es lo que dice el resto
//      de la aplicacion cuando habla de donde se abre una cosa, y el
//      nombre se traduce en un solo sitio en vez de en cada uso.
//
//   2. Cierra con v-model:open y avisa con close. Traducir el estado es
//      tarea del padre: este componente decide cuando abrir, nunca.
//
//   3. Un pie con un boton de cerrar. Es lo unico que casi todos los
//      drawers necesitan, y que cada uno lo escriba es copiar codigo
//      hasta que alguien lo cambia en un sitio y se olvida del otro.
//
// Slots: #body (el contenido) y #footer, que sustituye el pie entero
// cuando el drawer hace otra cosa.
//
// OJO con `side="bottom"`: en vertical el tirador de arrastre (handle)
// aparece solo si direction es top o bottom, asi que el boton de cerrar
// pasa a ser la unica salida visible. Por eso sigue ahi.
const open = defineModel<boolean>('open', { required: true })

// Sin `const props =`: en este componente los props se leen todos en la
// plantilla y ninguno en el script. La variable solo haria falta si
// onConfirm u otra funcion del script tuvieran que mirar un prop.
withDefaults(defineProps<{
  title: string
  /** Lado del que sale. UDrawer lo llama direction. */
  side?: 'left' | 'right' | 'top' | 'bottom'
  description?: string
  /** Texto del boton de cerrar. */
  closeLabel?: string
  /** Cierra con Escape, la capa o deslizando. */
  dismissible?: boolean
}>(), {
  side: 'right',
  description: '',
  closeLabel: 'Cerrar',
  dismissible: true
})

const emit = defineEmits<{
  close: []
}>()

function onClose() {
  open.value = false
  emit('close')
}

// La capa, el tirador y Escape pasan por update:open con false. Sin esto,
// cerrar deslizando el dedo emitiria update pero no close, y el padre se
// quedaria creyendo que el drawer sigue abierto y sincronizando datos
// que ya no ve nadie.
function onOpenChange(value: boolean) {
  open.value = value

  if (!value) {
    emit('close')
  }
}
</script>

<template>
  <UDrawer
    v-model:open="open"
    :title="title"
    :description="description"
    :direction="side"
    :dismissible="dismissible"
    :handle="side === 'top' || side === 'bottom'"
    @update:open="onOpenChange"
  >
    <template #body>
      <slot name="body" />
    </template>

    <template #footer>
      <slot name="footer">
        <div class="flex w-full justify-end">
          <UButton
            :label="closeLabel"
            color="neutral"
            variant="outline"
            icon="i-lucide-x"
            @click="onClose"
          />
        </div>
      </slot>
    </template>
  </UDrawer>
</template>
