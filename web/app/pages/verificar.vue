<script setup lang="ts">
// ======================================================================
// VERIFICACIÓN POR CÓDIGO (OTP)
//
// Segundo paso cuando el login responde 202. El challengeId viene por query
// (lo pone la página de login); el código son 6 dígitos que caducan junto al
// challenge en 5 minutos y admiten 5 intentos.
//
// El reenvío tiene un enfriamiento de 30 s que marca Spring con Retry-After
// en el 429; aquí se replica en local para no pedirlo dos veces.
// ======================================================================
import type { ApiError } from '~/composables/useApi'

definePageMeta({ layout: 'auth' })
useSeoMeta({ title: 'Verificación — pepedev', robots: 'noindex' })

const OTP_LENGTH = 6
const RESEND_COOLDOWN_SECONDS = 30

const route = useRoute()
const auth = useAuthApi()
const { fetch: fetchSession } = useUserSession()
const { notify } = useAppNotify()

const challengeId = typeof route.query.challenge === 'string' ? route.query.challenge : ''
const redirectTarget = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin'

const code = ref<number[]>([])
const error = ref('')
const loading = ref(false)
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | undefined

function startCooldown(seconds: number) {
  cooldown.value = seconds
  clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearInterval(cooldownTimer)
    }
  }, 1000)
}

onBeforeUnmount(() => clearInterval(cooldownTimer))

async function verify() {
  const value = code.value.join('')
  if (loading.value || value.length !== OTP_LENGTH) {
    return
  }

  error.value = ''
  loading.value = true
  try {
    await auth.verifyOtp(challengeId, value)
    await fetchSession()
    notify('Sesión iniciada')
    await navigateTo(redirectTarget)
  } catch (err) {
    const problem = (err as ApiError).problem
    error.value = problem?.detail ?? 'Código incorrecto'
    // El código se consume en cada intento: se limpia para el siguiente.
    code.value = []
  } finally {
    loading.value = false
  }
}

async function resend() {
  if (cooldown.value > 0) {
    return
  }

  error.value = ''
  try {
    await auth.resendOtp(challengeId)
    notify('Código reenviado')
    startCooldown(RESEND_COOLDOWN_SECONDS)
  } catch (err) {
    const problem = (err as ApiError).problem
    if (problem?.status === 429 && problem.retryAfter) {
      startCooldown(problem.retryAfter)
      error.value = `Podrás pedir otro código en ${problem.retryAfter} s.`
    } else if (problem?.status === 401) {
      error.value = 'La verificación ha caducado. Vuelve a iniciar sesión.'
    } else {
      error.value = problem?.detail ?? 'No se pudo reenviar el código'
    }
  }
}
</script>

<template>
  <UPageCard class="w-full max-w-md">
    <UAlert
      v-if="!challengeId"
      color="error"
      variant="subtle"
      icon="i-lucide-link-2-off"
      title="Verificación no válida"
      description="Falta el identificador del intento de acceso. Vuelve a iniciar sesión."
    />

    <template v-else>
      <div class="space-y-2 text-center">
        <UIcon
          name="i-lucide-mail-check"
          class="text-primary mx-auto size-10"
        />
        <h1 class="text-highlighted text-xl font-semibold">
          Verifica tu identidad
        </h1>
        <p class="text-muted text-sm">
          Hemos enviado un código de 6 dígitos a tu correo. Caduca en 5 minutos.
        </p>
      </div>

      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-circle-x"
        :title="error"
      />

      <form
        class="flex flex-col items-center gap-4"
        @submit.prevent="verify"
      >
        <UPinInput
          v-model="code"
          :length="OTP_LENGTH"
          type="number"
          otp
          autofocus
          :disabled="loading"
          aria-label="Código de verificación"
          @complete="verify"
        />

        <UButton
          type="submit"
          label="Verificar"
          block
          :loading="loading"
          :disabled="code.join('').length !== OTP_LENGTH"
        />
      </form>

      <p class="text-muted text-center text-sm">
        ¿No te ha llegado?
        <UButton
          variant="link"
          color="primary"
          :disabled="cooldown > 0 || loading"
          :label="cooldown > 0 ? `Reenviar en ${cooldown} s` : 'Reenviar código'"
          @click="resend"
        />
      </p>
    </template>

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
