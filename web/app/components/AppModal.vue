<script setup lang="ts">
// ======================================================================
// MODAL REUTILIZABLE
// Envoltorio de <UModal> con el esqueleto que casi siempre se repite:
// titulo, descripcion, cuerpo libre y un pie con cancelar y confirmar.
//
// Lo que aporta y no hay que reescribir en cada sitio:
//
//   1. v-model:open en vez de :open + @update:open. El estado vive fuera
//      del componente, asi que el padre decide cuando abrir.
//   2. Los dos botones del pie, con sus variantes y su disposicion. El de
//      confirmar acepta un confirmLoading para que un guardado asincrono
//      no acepte un segundo clic mientras vuela la peticion.
//   3. Un solo sitio donde tocar el aspecto del pie. Si manana los
//      botones van a la izquierda, se cambia aqui y no en quince paginas.
//
// Lo que NO hace a proposito, porque son decisiones del padre y aqui
// serian diez decisiones distintas:
//
//   - Que cerrar sea bloqueable. Si el padre quiere confirmar antes de
//     cerrar, lo hace con el emit, no con un preventDefault escondido.
//     Por eso confirm no cierra el modal: el padre decide que pasa
//     después, y si quiere cerrarlo hace open = false.
//   - El texto de los botones. Cancelar y Confirmar sirven en el 90% de
//     los casos; el 10% restante pasa por los slots, no por props sueltas.
//
// Slots: #body (dentro del scroll) y #footer, que sustituye el pie
// entero cuando hace falta algo que no sean dos botones.
//
// `open` va con defineModel y no con un prop suelto porque hace las dos
// cosas a la vez: declara la prop y el update. Con un `open: boolean` a
// mano habria que escribir `open: false` en onCancel, y en JS plano
// escribir una prop es escribir en el sitio de otro: Vue loeria en
// desarrollo y en la practice se pierde. defineModel devuelve un ref.
const open = defineModel<boolean>('open', { required: true })

const props = withDefaults(defineProps<{
  title: string
  description?: string
  /** Texto del boton de confirmar. */
  confirmLabel?: string
  /** Texto del boton de cancelar. */
  cancelLabel?: string
  /** Color del boton de confirmar. error para los destructivos. */
  confirmColor?: 'primary' | 'error'
  /** Deshabilita confirmar sin cerrar. Util con contenido obligatorio. */
  confirmDisabled?: boolean
  /** Estado de espera de la accion de confirmar. Lo pone el padre: este
   *  componente no sabe cuando termina la peticion que dispara confirm. */
  confirmLoading?: boolean
  /** Cierra con Escape, clic en la capa o la X. */
  dismissible?: boolean
}>(), {
  description: '',
  confirmLabel: 'Confirmar',
  cancelLabel: 'Cancelar',
  confirmColor: 'primary',
  confirmDisabled: false,
  confirmLoading: false,
  dismissible: true
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

// El modal sigue abierto mientras el padre no lo cierre, asi que confirmar
// no lo cierra: un guardado asincrono que cerrara el modal al pulsar el
// boton dejaria al usuario mirando el formulario mientras la peticion
// vuela, y si la peticion falla ya no hay formulario que reintentar. El
// padre hace open = false cuando le cuadra, y mientras tanto pone
// confirmLoading para que el boton no acepte un segundo clic.
function onConfirm() {
  if (props.confirmDisabled || props.confirmLoading) {
    return
  }

  emit('confirm')
}

// Cancelar cierra siempre. Si hay un formulario con cambios sin guardar,
// el padre decide antes de cerrar; para eso esta el emit y no un
// preventDefault.
function onCancel() {
  emit('cancel')
  open.value = false
}

// La capa, la X y Escape pasan por update:open con false. Sin esto, cerrar
// el modal por la X no emitiria cancel, y el padre se quedaria creyendo que
// el usuario confirmo.
function onOpenChange(value: boolean) {
  open.value = value

  if (!value) {
    emit('cancel')
  }
}
</script>

<template>
  <UModal
    :open="open"
    :title="title"
    :description="description"
    :dismissible="dismissible"
    @update:open="onOpenChange"
  >
    <template #body>
      <slot name="body" />
    </template>

    <template #footer>
      <slot name="footer">
        <div class="flex w-full justify-end gap-2">
          <UButton
            :label="cancelLabel"
            color="neutral"
            variant="outline"
            @click="onCancel"
          />

          <UButton
            :label="confirmLabel"
            :color="confirmColor"
            :disabled="confirmDisabled"
            :loading="confirmLoading"
            @click="onConfirm"
          />
        </div>
      </slot>
    </template>
  </UModal>
</template>
