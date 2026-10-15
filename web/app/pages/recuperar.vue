<script setup lang="ts">
// ======================================================================
// RECUPERAR CONTRASEÑA (solicitud)
//
// Pide el email y llama al BFF, que responde 202 siempre para no revelar si
// la cuenta existe. Por eso, tanto si el email está registrado como si no,
// se muestra el mismo aviso de "revisa tu correo".
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
    autocomplete: 'username'
  }
]

const schema = z.object({
  email: z.email('Introduce un email válido')
})
type Schema = z.output<typeof schema>

const sent = ref(false)
const formError = ref('')

async function onSubmit(event: FormSubmitEvent<Schema>) {
  formError.value = ''

  try {
    await auth.requestPasswordReset(event.data.email)
    sent.value = true
  } catch (error) {
    const problem = (error as ApiError).problem
    // El 429 trae los segundos de espera en Retry-After, no en el body.
    formError.value = problem?.status === 429 && problem.retryAfter
      ? `Demasiadas solicitudes. Inténtalo de nuevo en ${problem.retryAfter} s.`
      : problem?.detail ?? 'No se pudo enviar la solicitud'
  }
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
      description="Si el email está registrado, recibirás un enlace para restablecer la contraseña. El enlace caduca en 30 minutos."
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
        />
      </template>
    </UAuthForm>

    <template #footer>
      <ULink
        to="/login"
        class="text-primary font-medium"
      >
        Volver al inicio de sesión
      </ULink>
    </template>
  </UPageCard>
</template>
