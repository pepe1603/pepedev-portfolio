<script setup lang="ts">
// ======================================================================
// SECCION DE CONTACTO (id="contacto")
//
// Adaptada del formulario de la plantilla (UForm + Zod, era AppForm.vue):
// mismo patrón de schema, mismos errores por campo, pero con los campos
// de ContactRequest y el envío real a POST /contact (docs/API.md).
//
// --------------------------------------------------------------------------
// POR QUE LOS LIMITES DEL SCHEMA SON LOS MISMOS QUE LOS DE LA API
//
// name ≤120, email ≤320, subject ≤160, body 10..5000: si el schema de
// aquí fuera más laxo, el usuario escribiría, esperaría el viaje a la API
// y recibiría un 400 que el formulario podía ver venir sin salir de casa.
//
// --------------------------------------------------------------------------
// QUE PASA CON LOS ERRORES DE LA API
//
// Tres casos, los mismos que contempla el contrato:
//
//   400 → el envelope trae errors[] con un entry por campo; se vuelca al
//         formulario con setErrors para que cada mensaje aparezca debajo
//         del campo que lo provocó.
//   429 → rate limit por IP. Se respeta Retry-After cuando la API lo manda.
//   resto → aviso genérico con el detail del ProblemDetail.
//
// --------------------------------------------------------------------------
// EL HONEYPOT
//
// El campo `website` no lo ve nadie (hidden + tabindex -1). Los bots de
// formularios suelen rellenar todo lo que encuentran; si website llega
// relleno, la API responde un 201 falso idéntico al real y no persiste
// nada. Aquí no se comprueba nada: la decisión es del backend.
// ======================================================================
import { z } from 'zod'

const api = useApi()
const toast = useToast()

const schema = z.object({
  name: z.string()
    .min(2, { error: 'El nombre necesita al menos 2 caracteres.' })
    .max(120, { error: 'El nombre no puede pasar de 120 caracteres.' }),
  email: z.email({ error: 'Ese correo no tiene forma de correo.' })
    .max(320, { error: 'El correo no puede pasar de 320 caracteres.' }),
  subject: z.string()
    .min(1, { error: 'Escribe un asunto.' })
    .max(160, { error: 'El asunto no puede pasar de 160 caracteres.' }),
  body: z.string()
    .min(10, { error: 'El mensaje necesita al menos 10 caracteres.' })
    .max(5000, { error: 'El mensaje no puede pasar de 5000 caracteres.' }),
  // Honeypot: solo se pide que exista y que no sea gigante. Quien decide
  // si un valor es sospechoso es la API, no el schema.
  website: z.string().max(200).optional()
})

// El tipo sale del schema, no se escribe a mano.
type State = z.infer<typeof schema>

const state = reactive<State>({
  name: '',
  email: '',
  subject: '',
  body: '',
  website: ''
})

// UForm expone su API con un ref: hace falta para limpiar tras enviar y
// para volcar los errores 400 campo a campo.
const form = useTemplateRef('form')

const isSending = ref(false)

async function onSubmit({ data }: { data: State }) {
  // `data` ya viene validado y con los tipos del schema.
  isSending.value = true

  try {
    await api.post('/contact', data)

    toast.add({
      title: 'Mensaje enviado',
      description: 'Gracias por escribir; te contestaré lo antes posible.',
      icon: 'i-lucide-circle-check',
      color: 'success'
    })

    // Volver a escribir el estado campo a campo en vez de recrear el
    // reactive: el objeto es el mismo y el formulario sigue enlazado.
    Object.assign(state, { name: '', email: '', subject: '', body: '', website: '' })
    form.value?.clear()
  } catch (error) {
    // useApi normaliza todos los errores en ApiError con su ProblemDetail.
    const problem = error instanceof ApiError ? error.problem : null

    if (problem?.status === 400 && problem.errors?.length) {
      form.value?.setErrors(
        problem.errors
          .filter(entry => entry.field)
          .map(entry => ({ name: entry.field as string, message: entry.message }))
      )

      toast.add({
        title: 'Revisa el formulario',
        description: 'La API ha rechazado algún campo.',
        icon: 'i-lucide-triangle-alert',
        color: 'warning'
      })
    } else if (problem?.status === 429) {
      toast.add({
        title: 'Demasiados mensajes',
        description: problem.retryAfter
          ? `Vuelve a intentarlo en ${problem.retryAfter} segundos.`
          : 'Vuelve a intentarlo en unos segundos.',
        icon: 'i-lucide-clock',
        color: 'warning'
      })
    } else {
      toast.add({
        title: 'No se pudo enviar',
        description: problem?.detail ?? 'Inténtalo de nuevo en unos minutos.',
        icon: 'i-lucide-circle-x',
        color: 'error'
      })
    }
  } finally {
    isSending.value = false
  }
}

// @error sale cuando la validación LOCAL falla, con la lista de errores ya
// calculada. Sirve para el aviso genérico de "revisa el formulario".
function onError({ errors }: { errors: { name?: string, message: string }[] }) {
  toast.add({
    title: 'Revisa el formulario',
    description: `${errors.length} ${errors.length === 1 ? 'campo necesita' : 'campos necesitan'} atención.`,
    icon: 'i-lucide-triangle-alert',
    color: 'warning'
  })
}
</script>

<template>
  <UPageSection
    id="contacto"
    title="Contacto"
    description="Cuéntame lo que necesitas y te contesto directamente."
  >
    <UForm
      ref="form"
      :schema="schema"
      :state="state"
      class="flex max-w-xl flex-col gap-6"
      @submit="onSubmit"
      @error="onError"
    >
      <UFormField
        label="Nombre"
        name="name"
        required
      >
        <UInput
          v-model="state.name"
          placeholder="Tu nombre"
          icon="i-lucide-user"
          autocomplete="name"
          class="w-full"
        />
      </UFormField>

      <UFormField
        label="Correo"
        name="email"
        required
        description="Para poder contestarte."
      >
        <UInput
          v-model="state.email"
          type="email"
          inputmode="email"
          placeholder="tu@correo.com"
          icon="i-lucide-mail"
          autocomplete="email"
          class="w-full"
        />
      </UFormField>

      <UFormField
        label="Asunto"
        name="subject"
        required
      >
        <UInput
          v-model="state.subject"
          placeholder="En qué puedo ayudarte"
          icon="i-lucide-message-square"
          class="w-full"
        />
      </UFormField>

      <UFormField
        label="Mensaje"
        name="body"
        required
        description="Entre 10 y 5000 caracteres."
      >
        <UTextarea
          v-model="state.body"
          :rows="6"
          placeholder="Cuéntame el proyecto, la idea o la duda…"
          class="w-full"
        />
      </UFormField>

      <!-- Honeypot: fuera del flujo visual y del tab. -->
      <UFormField
        name="website"
        class="hidden"
      >
        <UInput
          v-model="state.website"
          tabindex="-1"
          autocomplete="off"
        />
      </UFormField>

      <div class="flex justify-end border-t border-default pt-6">
        <UButton
          type="submit"
          label="Enviar mensaje"
          icon="i-lucide-send"
          :loading="isSending"
        />
      </div>
    </UForm>
  </UPageSection>
</template>
