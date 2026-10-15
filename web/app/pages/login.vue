<script setup lang="ts">
// ======================================================================
// PÁGINA DE LOGIN
//
// Habla con el BFF (/api/auth/login), no con Spring. Si el OTP está activo,
// el login responde 202 con un challengeId y se salta a /verificar; si no,
// la cookie sellada ya está puesta y se entra al panel.
// ======================================================================
import * as z from 'zod'
import type { AuthFormField, FormSubmitEvent } from '@nuxt/ui'
import type { ApiError } from '~/composables/useApi'

definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Acceso — pepedev', robots: 'noindex' })

const route = useRoute()
const auth = useAuthApi()
const { fetch: fetchSession } = useUserSession()
const { notify } = useAppNotify()

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
    label: 'Contraseña',
    placeholder: '••••••••',
    required: true,
    autocomplete: 'current-password'
  }
]

const schema = z.object({
  email: z.email('Introduce un email válido'),
  password: z.string('La contraseña es obligatoria').min(1, 'La contraseña es obligatoria')
})
type Schema = z.output<typeof schema>

const formError = ref('')

async function onSubmit(event: FormSubmitEvent<Schema>) {
  formError.value = ''

  try {
    const result = await auth.login(event.data.email, event.data.password)

    // 202: el segundo factor está activo; todavía no hay sesión.
    if ('challengeId' in result) {
      await navigateTo({ path: '/verificar', query: { challenge: result.challengeId } })
      return
    }

    await fetchSession()
    notify(`Sesión iniciada como ${result.user.email}`)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin'
    await navigateTo(redirect)
  } catch (error) {
    formError.value = (error as ApiError).problem?.detail ?? 'No se pudo iniciar sesión'
  }
}
</script>

<template>
  <UPageCard class="w-full max-w-md">
    <UAuthForm
      :schema="schema"
      :fields="fields"
      title="Acceso al panel"
      description="Introduce tus credenciales para continuar."
      icon="i-lucide-shield-check"
      :submit="{ label: 'Entrar', block: true }"
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

      <template #footer>
        <ULink
          to="/"
          class="text-primary font-medium"
        >
          Volver al portfolio
        </ULink>
      </template>
    </UAuthForm>
  </UPageCard>
</template>
