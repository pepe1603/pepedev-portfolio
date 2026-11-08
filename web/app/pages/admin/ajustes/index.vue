<script setup lang="ts">
// ======================================================================
// AJUSTES
//
// Tres bloques, todos sobre la propia cuenta del admin:
//
//   1. Segundo factor (OTP): interruptor que lee GET /admin/otp y escribe
//      PATCH /admin/otp. El GET se añadió a la API justo para esto: antes
//      la preferencia solo se podía escribir. OJO: es la preferencia del
//      usuario, no el feature flag APP_AUTH_OTP_ENABLED; si el flag está
//      apagado, activar aquí no cambia el login (por eso se avisa).
//
//   2. Sesiones: GET /api/auth/sessions lista las activas y se puede
//      revocar una, revocar todas menos la actual, o cerrar todas (que
//      incluye la de este navegador y deja la sesión local limpia).
//
//   3. Contraseña: envía el correo de recuperación a la propia cuenta con
//      POST /api/auth/reset/request. Spring responde 202 siempre, así que
//      el mensaje es el mismo exista o no el correo.
//
// Todo pasa por el BFF: el navegador solo conoce la cookie sellada. La
// lista de sesiones sale de Nuxt (/api/auth/...) y el OTP del proxy
// (/api/admin/...), que ya reenvía el PATCH y ahora también el GET.
// ======================================================================
import { toApiError, unwrapApiError } from '~/composables/useApi'
import type { TableColumn } from '@nuxt/ui'
import type { AuthSession } from '#shared/types/auth'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const authApi = useAuthApi()
const { notify } = useAppNotify()
const { user } = useUserSession()

// El email de la propia cuenta, para mandarle el correo de recuperación.
// Va por el BFF /api/auth/me (claim de la cookie), no por Spring.
const ownEmail = computed(() => user.value?.email ?? '')

// ---------------------------------------------------------------------------
// OTP
//
// El $fetch de Nuxt no reenvía cookies durante el SSR, así que estas
// llamadas van solo en cliente (server: false), igual que el resto del
// panel. El PATCH responde 204 sin cuerpo: no se puede leer el valor de
// vuelta, se fija el optimista y se confirma releyendo.
// ---------------------------------------------------------------------------
const {
  data: otp,
  status: otpStatus,
  error: otpError,
  refresh: refreshOtp
} = await useAsyncData(
  'admin-otp',
  () => api.get<{ enabled: boolean }>('/otp'),
  { server: false }
)

const otpEnabled = ref(false)
const otpSaving = ref(false)

// Se copia el valor cargado al ref editable. Un watch (no un computed con
// setter) porque al guardar hay que poder revertir si la API rechaza.
watch(otp, (value) => {
  if (value) {
    otpEnabled.value = value.enabled
  }
}, { immediate: true })

const otpLoadError = computed(() => unwrapApiError(otpError.value))
const otpLoadErrorMessage = computed(
  () => otpLoadError.value?.problem.detail ?? otpLoadError.value?.message ?? 'No se pudo conectar con el servidor'
)

async function toggleOtp(next: boolean) {
  // El switch ya cambió en pantalla; se guarda el anterior para revertir.
  const previous = !next
  otpSaving.value = true
  try {
    await api.patch('/otp', { enabled: next })
    otpEnabled.value = next
    notify(next ? 'Segundo factor activado' : 'Segundo factor desactivado', 'success')
  } catch (error) {
    otpEnabled.value = previous
    const apiError = toApiError(error)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    otpSaving.value = false
  }
}

// ---------------------------------------------------------------------------
// SESIONES
// ---------------------------------------------------------------------------
const {
  data: sessions,
  status: sessionsStatus,
  error: sessionsError,
  refresh: refreshSessions
} = await useAsyncData(
  'admin-sessions',
  () => authApi.sessions(),
  { server: false }
)

const sessionCount = computed(() => sessions.value?.length ?? 0)
const otherSessionCount = computed(() => (sessions.value ?? []).filter(s => !s.current).length)

const sessionsLoadError = computed(() => unwrapApiError(sessionsError.value))
const sessionsLoadErrorMessage = computed(
  () => sessionsLoadError.value?.problem.detail ?? sessionsLoadError.value?.message ?? 'No se pudo conectar con el servidor'
)

const sessionColumns: TableColumn<AuthSession>[] = [
  { accessorKey: 'userAgent', header: 'Dispositivo' },
  { accessorKey: 'ipAddress', header: 'IP' },
  { accessorKey: 'lastSeenAt', header: 'Última actividad' },
  { accessorKey: 'expiresAt', header: 'Caduca' },
  { id: 'actions', header: '' }
]

// El user agent puede venir null o muy largo. Se etiqueta de forma legible
// sin fingir que se parsea: solo se recorta para no romper la tabla.
function deviceLabel(userAgent: string | null): string {
  if (!userAgent) {
    return 'Desconocido'
  }
  return userAgent.length > 48 ? `${userAgent.slice(0, 45)}…` : userAgent
}

// Estado de las acciones (revocar una, revocar otras, cerrar todas). Un
// único flag para que no se puedan lanzar dos a la vez.
const sessionAction = ref<'revoke' | 'others' | 'all' | null>(null)
const revokeTarget = ref<AuthSession | null>(null)
const revokeOpen = ref(false)
const othersOpen = ref(false)
const logoutAllOpen = ref(false)

function askRevoke(session: AuthSession) {
  revokeTarget.value = session
  revokeOpen.value = true
}

async function confirmRevoke() {
  const target = revokeTarget.value
  if (!target) {
    return
  }
  sessionAction.value = 'revoke'
  try {
    await authApi.revokeSession(target.id)
    notify('Sesión cerrada', 'success')
    revokeOpen.value = false
    await refreshSessions()
  } catch (error) {
    const apiError = toApiError(error)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    sessionAction.value = null
  }
}

async function confirmRevokeOthers() {
  sessionAction.value = 'others'
  try {
    await authApi.revokeOtherSessions()
    notify('Se cerraron las demás sesiones', 'success')
    othersOpen.value = false
    await refreshSessions()
  } catch (error) {
    const apiError = toApiError(error)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    sessionAction.value = null
  }
}

async function confirmLogoutAll() {
  sessionAction.value = 'all'
  try {
    // Cierra TODAS, incluida la actual: el BFF limpia la cookie sellada,
    // así que después hay que salir al login. El middleware lo hará solo al
    // navegar, pero se fuerza aquí para no quedarse en una página muerta.
    await authApi.logoutAll()
    notify('Se cerraron todas las sesiones', 'success')
    await navigateTo('/login')
  } catch (error) {
    const apiError = toApiError(error)
    notify(apiError.problem.detail || apiError.message, 'error')
    sessionAction.value = null
  }
}

// ---------------------------------------------------------------------------
// CONTRASEÑA
//
// Solo se pide el correo, que es el de la propia cuenta. El envío es un
// POST /api/auth/reset/request; Spring responde 202 siempre.
// ---------------------------------------------------------------------------
const resetSending = ref(false)
const resetSent = ref(false)

async function sendResetEmail() {
  if (!ownEmail.value) {
    return
  }
  resetSending.value = true
  try {
    await authApi.requestPasswordReset(ownEmail.value)
    resetSent.value = true
    notify('Correo de recuperación enviado', 'success')
  } catch (error) {
    const apiError = toApiError(error)
    notify(apiError.problem.detail || apiError.message, 'error')
  } finally {
    resetSending.value = false
  }
}
</script>

<template>
  <UDashboardPanel id="admin-ajustes">
    <template #header>
      <UDashboardNavbar title="Ajustes">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="flex max-w-4xl flex-col gap-6">
        <!-- ============================ SEGURIDAD ============================ -->
        <section class="flex flex-col gap-3">
          <div>
            <h2 class="font-semibold text-highlighted">
              Seguridad
            </h2>
            <p class="text-muted text-sm">
              Opciones de acceso para la cuenta
              <span class="font-medium text-highlighted">{{ ownEmail }}</span>.
            </p>
          </div>

          <UCard>
            <!-- Cargando el estado de OTP -->
            <div
              v-if="otpStatus === 'pending'"
              class="flex items-center justify-between"
              aria-hidden="true"
            >
              <USkeleton class="h-5 w-48" />
              <USkeleton class="h-6 w-11 rounded-full" />
            </div>

            <!-- Error al leer el estado -->
            <div
              v-else-if="otpError"
              class="flex flex-col items-start gap-3"
            >
              <div>
                <p class="font-medium text-highlighted">
                  No se pudo leer el estado del segundo factor
                </p>
                <p class="text-muted text-sm">
                  {{ otpLoadErrorMessage }}
                </p>
              </div>
              <UButton
                label="Reintentar"
                icon="i-lucide-refresh-cw"
                color="neutral"
                variant="soft"
                @click="refreshOtp()"
              />
            </div>

            <!-- Interruptor de OTP -->
            <div
              v-else
              class="flex items-start justify-between gap-4"
            >
              <div class="flex flex-col gap-1">
                <p class="font-medium text-highlighted">
                  Segundo factor por email
                </p>
                <p class="text-muted text-sm">
                  Al iniciar sesión, además de la contraseña se pedirá un código enviado por email.
                </p>
              </div>
              <USwitch
                :model-value="otpEnabled"
                :loading="otpSaving"
                :disabled="otpSaving"
                aria-label="Activar segundo factor por email"
                @update:model-value="toggleOtp"
              />
            </div>

            <template #footer>
              <p class="text-muted text-xs">
                Es tu preferencia de cuenta. Si el servidor tiene el segundo factor desactivado globalmente, activarlo
                aquí no cambiará el inicio de sesión; el cambio sí queda guardado y se aplicará cuando el servidor lo
                permita.
              </p>
            </template>
          </UCard>
        </section>

        <!-- ============================ SESIONES ============================ -->
        <section class="flex flex-col gap-3">
          <div class="flex flex-wrap items-end justify-between gap-3">
            <div>
              <h2 class="font-semibold text-highlighted">
                Sesiones activas
              </h2>
              <p class="text-muted text-sm">
                Dispositivos con la sesión abierta. Cierra las que no reconozcas.
              </p>
            </div>

            <div class="flex flex-wrap gap-2">
              <UButton
                v-if="otherSessionCount > 0"
                label="Cerrar las demás"
                icon="i-lucide-log-out"
                color="neutral"
                variant="soft"
                :disabled="sessionAction !== null"
                @click="othersOpen = true"
              />
              <UButton
                v-if="sessionCount > 0"
                label="Cerrar todas"
                icon="i-lucide-shield-off"
                color="error"
                variant="soft"
                :disabled="sessionAction !== null"
                @click="logoutAllOpen = true"
              />
            </div>
          </div>

          <!-- Cargando sesiones -->
          <div
            v-if="sessionsStatus === 'pending'"
            class="space-y-2"
            aria-hidden="true"
          >
            <USkeleton
              v-for="i in 3"
              :key="i"
              class="h-12 w-full"
            />
          </div>

          <!-- Error al cargar sesiones -->
          <UCard
            v-else-if="sessionsError"
            variant="subtle"
          >
            <div class="flex flex-col items-start gap-3">
              <div>
                <p class="font-medium text-highlighted">
                  No se pudieron cargar las sesiones
                </p>
                <p class="text-muted text-sm">
                  {{ sessionsLoadErrorMessage }}
                </p>
              </div>
              <UButton
                label="Reintentar"
                icon="i-lucide-refresh-cw"
                color="neutral"
                variant="soft"
                @click="refreshSessions()"
              />
            </div>
          </UCard>

          <!-- Sin sesiones -->
          <UCard
            v-else-if="!sessions?.length"
            variant="subtle"
          >
            <p class="text-muted text-sm">
              No hay sesiones activas que mostrar.
            </p>
          </UCard>

          <div
            v-else
            class="flex flex-col gap-3"
          >
            <!-- Tarjetas en movil: la tabla tiene 5 columnas y no cabe en
                 una pantalla estrecha; con scroll horizontal el boton
                 Cerrar quedaba fuera. A partir de sm manda la tabla. -->
            <div class="flex flex-col gap-3 sm:hidden">
              <UCard
                v-for="session in sessions"
                :key="session.id"
                :class="session.current ? 'ring-2 ring-primary' : ''"
              >
                <div class="flex flex-col gap-2">
                  <div class="flex flex-wrap items-center gap-2">
                    <span class="font-medium text-highlighted">
                      {{ deviceLabel(session.userAgent) }}
                    </span>
                    <UBadge
                      v-if="session.current"
                      color="primary"
                      variant="soft"
                      size="sm"
                    >
                      Esta sesión
                    </UBadge>
                  </div>

                  <dl class="text-sm">
                    <div class="flex justify-between gap-3 py-0.5">
                      <dt class="text-muted">
                        IP
                      </dt>
                      <dd class="text-highlighted">
                        {{ session.ipAddress || '—' }}
                      </dd>
                    </div>
                    <div class="flex justify-between gap-3 py-0.5">
                      <dt class="text-muted">
                        Última actividad
                      </dt>
                      <dd class="text-highlighted">
                        {{ adminDate(session.lastSeenAt, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) }}
                      </dd>
                    </div>
                    <div class="flex justify-between gap-3 py-0.5">
                      <dt class="text-muted">
                        Caduca
                      </dt>
                      <dd class="text-highlighted">
                        {{ adminDate(session.expiresAt) }}
                      </dd>
                    </div>
                  </dl>
                </div>

                <template #footer>
                  <UButton
                    label="Cerrar"
                    icon="i-lucide-x"
                    color="error"
                    variant="soft"
                    size="sm"
                    block
                    :disabled="sessionAction !== null"
                    @click="askRevoke(session)"
                  />
                </template>
              </UCard>
            </div>

            <UTable
              class="hidden sm:block"
              :data="sessions"
              :columns="sessionColumns"
              aria-label="Sesiones activas"
            >
              <template #userAgent-cell="{ row }">
                <div class="flex items-center gap-2">
                  <span class="text-highlighted">
                    {{ deviceLabel(row.original.userAgent) }}
                  </span>
                  <UBadge
                    v-if="row.original.current"
                    color="primary"
                    variant="soft"
                    size="sm"
                  >
                    Esta sesión
                  </UBadge>
                </div>
              </template>

              <template #ipAddress-cell="{ row }">
                <span class="text-muted text-sm">
                  {{ row.original.ipAddress || '—' }}
                </span>
              </template>

              <template #lastSeenAt-cell="{ row }">
                <span class="text-muted text-sm">
                  {{ adminDate(row.original.lastSeenAt, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) }}
                </span>
              </template>

              <template #expiresAt-cell="{ row }">
                <span class="text-muted text-sm">
                  {{ adminDate(row.original.expiresAt) }}
                </span>
              </template>

              <template #actions-cell="{ row }">
                <div class="flex justify-end">
                  <UButton
                    label="Cerrar"
                    icon="i-lucide-x"
                    color="error"
                    variant="ghost"
                    size="xs"
                    :disabled="sessionAction !== null"
                    @click="askRevoke(row.original)"
                  />
                </div>
              </template>
            </UTable>
          </div>
        </section>

        <!-- ============================ CONTRASEÑA ============================ -->
        <section class="flex flex-col gap-3">
          <div>
            <h2 class="font-semibold text-highlighted">
              Contraseña
            </h2>
            <p class="text-muted text-sm">
              Cambiar la contraseña se hace por email, con un enlace de un solo uso.
            </p>
          </div>

          <UCard>
            <div class="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
              <p class="text-muted text-sm">
                Te enviaremos un enlace a
                <span class="font-medium text-highlighted">{{ ownEmail }}</span>
                para elegir una contraseña nueva.
              </p>
              <UButton
                label="Enviar enlace"
                icon="i-lucide-mail"
                :loading="resetSending"
                :disabled="!ownEmail || resetSending"
                @click="sendResetEmail"
              />
            </div>

            <template
              v-if="resetSent"
              #footer
            >
              <p class="text-muted text-xs">
                Si el correo existe, recibirás el enlace en unos minutos. Revisa también la carpeta de spam. Por
                seguridad no se confirma si la cuenta está registrada.
              </p>
            </template>
          </UCard>
        </section>
      </div>

      <!-- ================= MODAL: revocar una sesión ================= -->
      <UModal
        v-model:open="revokeOpen"
        title="¿Cerrar esta sesión?"
        :dismissible="sessionAction === null"
        :close="sessionAction === null"
      >
        <template #body>
          <p
            v-if="revokeTarget"
            class="text-muted text-sm"
          >
            Se cerrará la sesión de
            <span class="font-medium text-highlighted">{{ deviceLabel(revokeTarget.userAgent) }}</span>.
            Si es otro dispositivo, tendrá que volver a iniciar sesión. Esta acción no se puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="sessionAction !== null"
              @click="revokeOpen = false"
            />
            <UButton
              label="Cerrar sesión"
              icon="i-lucide-log-out"
              color="error"
              :loading="sessionAction === 'revoke'"
              @click="confirmRevoke"
            />
          </div>
        </template>
      </UModal>

      <!-- ================= MODAL: revocar las demás ================= -->
      <UModal
        v-model:open="othersOpen"
        title="¿Cerrar las demás sesiones?"
        :dismissible="sessionAction === null"
        :close="sessionAction === null"
      >
        <template #body>
          <p class="text-muted text-sm">
            Se cerrarán todas las sesiones menos esta. Los demás dispositivos tendrán que volver a iniciar sesión.
            Esta acción no se puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="sessionAction !== null"
              @click="othersOpen = false"
            />
            <UButton
              label="Cerrar las demás"
              icon="i-lucide-log-out"
              color="error"
              :loading="sessionAction === 'others'"
              @click="confirmRevokeOthers"
            />
          </div>
        </template>
      </UModal>

      <!-- ================= MODAL: cerrar todas ================= -->
      <UModal
        v-model:open="logoutAllOpen"
        title="¿Cerrar todas las sesiones?"
        :dismissible="sessionAction === null"
        :close="sessionAction === null"
      >
        <template #body>
          <p class="text-muted text-sm">
            Se cerrarán <span class="font-medium text-highlighted">todas</span> las sesiones, incluida esta. Saldrás
            de la cuenta y tendrás que volver a iniciar sesión. Esta acción no se puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="sessionAction !== null"
              @click="logoutAllOpen = false"
            />
            <UButton
              label="Cerrar todas y salir"
              icon="i-lucide-shield-off"
              color="error"
              :loading="sessionAction === 'all'"
              @click="confirmLogoutAll"
            />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
