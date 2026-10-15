<script setup lang="ts">
// ======================================================================
// RESTABLECER CONTRASEÑA (confirmación)
//
// El enlace del correo solo trae `?token=...`, pero el endpoint exige también
// el email, así que el formulario lo pide. Si falta el token, el enlace es
// inválido y no tiene sentido pintar el formulario.
//
// Al confirmar (204), Spring revoca todas las sesiones: se vuelve al login.
// ======================================================================
import * as z from 'zod'
import type { AuthFormField, FormSubmitEvent } from '@nuxt/ui'
import type { ApiError } from '~/composables/useApi'

definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Nueva contraseña — pepedev', robots: 'noindex' })

const route = useRoute()
const auth = useAuthApi()
const { notify } = useAppNotify()

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))

const fields: AuthFormField[] = [
  {
    name: 'email',
    type: 'email',
    label: 'Email',
    placeholder: 'tu@email.com',
    required: true,
    autocomplete: 'username'
  },
  {
    name: 'password',
    type: 'password',
    label: 'Nueva contraseña',
    placeholder: 'Mínimo 8 caracteres',
    required: true,
    autocomplete: 'new-password'
  },
  {
    name: 'passwordConfirmation',
    type: 'password',
    label: 'Repite la contraseña',
    placeholder: '••••••••',
    required: true,
    autocomplete: 'new-password'
  }
]

const schema = z.object({
  email: z.email('Introduce un email válido'),
  password: z.string('La contraseña es obligatoria').min(8, 'La contraseña debe tener al menos 8 caracteres'),
  passwordConfirmation: z.string('Repite la contraseña').min(1, 'Repite la contraseña')
}).refine(data => data.password === data.passwordConfirmation, {
  message: 'Las contraseñas no coinciden',
  path: ['passwordConfirmation']
})
type Schema = z.output<typeof schema>

const formError = ref('')

async function onSubmit(event: FormSubmitEvent<Schema>) {
  formError.value = ''

  try {
    await auth.confirmPasswordReset(event.data.email, token.value, event.data.password)
    notify('Contraseña actualizada. Inicia sesión con la nueva.')
    await navigateTo('/login')
  } catch (error) {
    formError.value = (error as ApiError).problem?.detail ?? 'No se pudo actualizar la contraseña'
  }
}
</script>

<template>
  <UPageCard class="w-full max-w-md">
    <UAlert
      v-if="!token"
      color="error"
      variant="subtle"
      icon="i-lucide-link-2-off"
      title="Enlace no válido"
      description="El enlace no incluye el token de recuperación. Solicita uno nuevo."
    />

    <UAuthForm
      v-else
      :schema="schema"
      :fields="fields"
      title="Nueva contraseña"
      description="Elige una contraseña de al menos 8 caracteres."
      icon="i-lucide-key-round"
      :submit="{ label: 'Guardar contraseña', block: true }"
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
        to="/recuperar"
        class="text-primary font-medium"
      >
        {{ token ? 'Solicitar otro enlace' : 'Solicitar un enlace' }}
      </ULink>
    </template>
  </UPageCard>
</template>
