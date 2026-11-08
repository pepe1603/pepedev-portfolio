<script setup lang="ts">
// ======================================================================
// RECUPERAR CONTRASEÑA (solicitud)
//
// Pide el email y llama al BFF, que responde 202 siempre para no revelar si
// la cuenta existe. Por eso, tanto si el email está registrado como si no,
// se muestra el mismo aviso de "revisa tu correo".
//
// El email tecleado sí se reutiliza en el aviso de éxito: nombrarlo no revela
// nada (lo acaba de escribir el propio usuario) y evita el "¿a qué correo?",
// que es la duda típica al no saber a qué bandeja mirar. Desde ahí se puede
// volver a intentarlo con otro email.
// ======================================================================
import * as z from 'zod'
import type { AuthFormField, FormSubmitEvent } from '@nuxt/ui'
import type { ApiError } from '~/composables/useApi'

definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Recuperar contraseña — pepedev', robots: 'noindex' })

const auth = useAuthApi()

const fields: AuthFormField[] = [
  {
    name: 'email',
    type: 'email',
    label: 'Email',
    placeholder: 'tu@email.com',
    required: true,
    autocomplete: 'username',
    autofocus: true
  }
]

const schema = z.object({
  email: z.email('Introduce un email válido')
})
type Schema = z.output<typeof schema>

const sent = ref(false)
// Email al que se envió el enlace; solo se usa para el aviso de éxito.
const sentTo = ref('')
const formError = ref('')

const sentDescription = computed(() => sentTo.value
  ? `Si ${sentTo.value} está registrado, recibirás un enlace para restablecer la contraseña. El enlace caduca en 30 minutos.`
  : 'Si el email está registrado, recibirás un enlace para restablecer la contraseña. El enlace caduca en 30 minutos.')

async function onSubmit(event: FormSubmitEvent<Schema>) {
  formError.value = ''

  try {
    await auth.requestPasswordReset(event.data.email)
    sentTo.value = event.data.email
    sent.value = true
  } catch (error) {
    const problem = (error as ApiError).problem
    // El 429 trae los segundos de espera en Retry-After, no en el body.
    formError.value = problem?.status === 429 && problem.retryAfter
      ? `Demasiadas solicitudes. Inténtalo de nuevo en ${problem.retryAfter} s.`
      : problem?.detail ?? 'No se pudo enviar la solicitud'
  }
}

/** Vuelve al formulario para probar con otro email. */
function reset() {
  sent.value = false
  sentTo.value = ''
  formError.value = ''
}
</script>

<template>
  <UPageCard class="w-full max-w-md">
    <UAlert
      v-if="sent"
      color="success"
      variant="subtle"
      icon="i-lucide-mail-check"
      title="Revisa tu correo"
      :description="sentDescription"
      role="status"
    />

    <UAuthForm
      v-else
      :schema="schema"
      :fields="fields"
      title="Recuperar contraseña"
      description="Te enviaremos un enlace para crear una nueva."
      icon="i-lucide-key-round"
      :submit="{ label: 'Enviar enlace', block: true }"
      loading-auto
      @submit="onSubmit"
    >
      <template #validation>
        <UAlert
          v-if="formError"
          color="error"
          variant="subtle"
          icon="i-lucide-circle-x"
          :title="formError"
          role="alert"
        />
      </template>
    </UAuthForm>

    <template #footer>
      <div class="flex flex-col items-center gap-1">
        <ULink
          to="/login"
          class="text-primary font-medium"
        >
          Volver al inicio de sesión
        </ULink>
        <UButton
          v-if="sent"
          variant="link"
          color="neutral"
          size="sm"
          label="Usar otro email"
          @click="reset"
        />
      </div>
    </template>
  </UPageCard>
</template>
