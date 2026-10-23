<script setup lang="ts">
import type { TableColumn, DropdownMenuItem } from '@nuxt/ui'
import { toApiError, unwrapApiError } from '~/composables/useApi'
import { CARD_INTERACTIVA } from '~/utils/cardStyles'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()
const { refreshUnread } = useUnreadMessages()

// ---------------------------------------------------------------------------
// PAGINACIÓN
//
// Los mensajes son lo ÚNICO paginado del panel: GET /admin/messages devuelve
// un PageResponse<Message>. Se pagina en el servidor (page/size en la query)
// en lugar de traerlo todo: una bandeja de entrada crece sin techo.
//
// UPagination va con páginas 1-based; a la API se le manda 0-based.
// ---------------------------------------------------------------------------
const PAGE_SIZE = 20

const page = ref(1)
const statusFilter = ref<'todos' | MessageStatus>('todos')

const statusOptions = [
  { label: 'Todos los mensajes', value: 'todos' },
  { label: 'Sin leer', value: 'NEW' },
  { label: 'Leídos', value: 'READ' },
  { label: 'Archivados', value: 'ARCHIVED' }
]

const { data, status, error, refresh } = await useAsyncData(
  // La key incluye página y filtro: cambiar cualquiera relanza la petición.
  () => `admin-messages-${statusFilter.value}-${page.value}`,
  () => api.get<AdminPage<AdminMessage>>('/messages', {
    page: page.value - 1,
    size: PAGE_SIZE,
    ...(statusFilter.value === 'todos' ? {} : { status: statusFilter.value })
  }),
  { server: false, watch: [page, statusFilter] }
)

const messages = computed(() => data.value?.items ?? [])
const totalElements = computed(() => data.value?.totalElements ?? 0)
const totalPages = computed(() => data.value?.totalPages ?? 0)

// Como en los otros listados: el estado de error se decide por `error`, no
// por el tipo de fallo; una API caída no llega como ProblemDetail.
const loadErrorMessage = computed(() => {
  const apiError = unwrapApiError(error.value)
  return apiError?.problem.detail ?? apiError?.message ?? 'No se pudo conectar con el servidor'
})

// Al cambiar de filtro se vuelve a la página 1 y el watcher de useAsyncData
// lo recoge en el mismo tick (una sola petición).
function onStatusChange() {
  page.value = 1
}

// ---------------------------------------------------------------------------
// COLUMNAS
// ---------------------------------------------------------------------------
const columns: TableColumn<AdminMessage>[] = [
  { id: 'sender', header: 'Remitente' },
  { id: 'subject', header: 'Asunto' },
  { id: 'status', header: 'Estado' },
  { id: 'createdAt', header: 'Recibido' },
  { id: 'actions', header: '' }
]

// ---------------------------------------------------------------------------
// ACCIONES
// ---------------------------------------------------------------------------
const pendingId = ref<string | null>(null)
const deleteTarget = ref<AdminMessage | null>(null)
const deleteOpen = ref(false)
const deleting = ref(false)

// Ficha abierta. El listado ya trae el cuerpo entero, así que no hace falta
// un GET /admin/messages/{id} adicional.
const selected = ref<AdminMessage | null>(null)
const detailOpen = ref(false)

function openDetail(message: AdminMessage) {
  selected.value = message
  detailOpen.value = true
}

// Tras cualquier cambio se recargan la página y el badge del sidebar. Si hay
// una ficha abierta, se re-sincroniza con el mensaje recién devuelto: refleja
// su nuevo estado y, si se ha borrado, se cierra sola.
async function afterMutation() {
  await Promise.all([refresh(), refreshUnread()])
  if (!selected.value) return
  const current = messages.value.find(m => m.id === selected.value!.id) ?? null
  selected.value = current
  if (!current) detailOpen.value = false
}

async function markRead(message: AdminMessage) {
  pendingId.value = message.id
  try {
    await api.patch(`/messages/${message.id}/read`)
    notify('Marcado como leído', 'success')
    await afterMutation()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    pendingId.value = null
  }
}

async function archive(message: AdminMessage) {
  pendingId.value = message.id
  try {
    await api.patch(`/messages/${message.id}/archive`)
    notify('Mensaje archivado', 'success')
    await afterMutation()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    pendingId.value = null
  }
}

function askDelete(message: AdminMessage) {
  // Si el borrado se pide desde la ficha, se cierra antes: dos modales
  // apilados a la vez solo confunden, y el de confirmación ya dice el nombre.
  detailOpen.value = false
  deleteTarget.value = message
  deleteOpen.value = true
}

async function confirmDelete() {
  const message = deleteTarget.value
  if (!message) return
  deleting.value = true
  try {
    await api.remove(`/messages/${message.id}`)
    notify('Mensaje eliminado', 'success')
    deleteOpen.value = false
    deleteTarget.value = null
    // Si era el último de la página, se retrocede una para no quedarse en
    // una página que ya no existe.
    if (messages.value.length === 1 && page.value > 1) {
      page.value -= 1
    }
    await afterMutation()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    deleting.value = false
  }
}

// El menú solo ofrece lo que tiene sentido en cada estado: no se marca como
// leído lo que ya lo está ni se archiva lo archivado.
function rowActions(message: AdminMessage): DropdownMenuItem[][] {
  const items: DropdownMenuItem[] = [
    { label: 'Abrir', icon: 'i-lucide-mail-open', onSelect: () => openDetail(message) }
  ]

  if (message.status === 'NEW') {
    items.push({
      label: 'Marcar como leído',
      icon: 'i-lucide-check',
      disabled: pendingId.value === message.id,
      onSelect: () => markRead(message)
    })
  }

  if (message.status !== 'ARCHIVED') {
    items.push({
      label: 'Archivar',
      icon: 'i-lucide-archive',
      disabled: pendingId.value === message.id,
      onSelect: () => archive(message)
    })
  }

  items.push({
    label: 'Borrar',
    icon: 'i-lucide-trash-2',
    color: 'error',
    disabled: pendingId.value === message.id,
    onSelect: () => askDelete(message)
  })

  return [items]
}
</script>

<template>
  <UDashboardPanel id="admin-mensajes">
    <template #header>
      <UDashboardNavbar title="Mensajes">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <ColorModeToggle />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Filtro por estado. El total viene de la API, no de la página. -->
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <USelect
          v-model="statusFilter"
          :items="statusOptions"
          class="w-full sm:w-56"
          aria-label="Filtrar por estado"
          @update:model-value="onStatusChange"
        />
        <p
          v-if="totalElements"
          class="text-muted ms-auto text-sm"
        >
          {{ totalElements }} {{ totalElements === 1 ? 'mensaje' : 'mensajes' }}
        </p>
      </div>

      <!-- Cargando. -->
      <div
        v-if="status === 'pending'"
        class="space-y-2"
        aria-hidden="true"
      >
        <USkeleton
          v-for="i in 5"
          :key="i"
          class="h-14 w-full"
        />
      </div>

      <!-- Error de carga. El 401 ya abre el modal de sesión caducada solo. -->
      <UCard
        v-else-if="error"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              No se pudieron cargar los mensajes
            </p>
            <p class="text-muted text-sm">
              {{ loadErrorMessage }}
            </p>
          </div>
          <UButton
            label="Reintentar"
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="soft"
            @click="refresh()"
          />
        </div>
      </UCard>

      <!-- Bandeja vacía de verdad (sin filtros). -->
      <UCard
        v-else-if="!totalElements && statusFilter === 'todos'"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-2">
          <p class="font-medium text-highlighted">
            Todavía no hay mensajes
          </p>
          <p class="text-muted text-sm">
            Aquí aparecerán los que reciba el formulario de contacto.
          </p>
        </div>
      </UCard>

      <!-- El filtro no deja nada. -->
      <UCard
        v-else-if="!totalElements"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Ningún mensaje en ese estado
            </p>
            <p class="text-muted text-sm">
              Prueba mostrando todos.
            </p>
          </div>
          <UButton
            label="Ver todos"
            icon="i-lucide-list"
            color="neutral"
            variant="soft"
            @click="statusFilter = 'todos'; onStatusChange()"
          />
        </div>
      </UCard>

      <template v-else>
        <!-- Tarjetas por debajo de lg: la tabla mide ~820 px y en pantallas
             estrechas el menu de acciones quedaba fuera de vista. La tarjeta
             entera abre la ficha (igual que la fila), salvo el menu. -->
        <div class="flex flex-col gap-3 lg:hidden">
          <UCard
            v-for="message in messages"
            :key="message.id"
            class="cursor-pointer"
            :ui="{ root: CARD_INTERACTIVA }"
            role="button"
            tabindex="0"
            :aria-label="`Abrir mensaje de ${message.name}`"
            @click="openDetail(message)"
            @keydown.enter.prevent="openDetail(message)"
            @keydown.space.prevent="openDetail(message)"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="flex min-w-0 flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span
                    class="truncate text-sm transition-colors group-hover:text-primary"
                    :class="message.status === 'NEW' ? 'font-semibold text-highlighted' : 'font-medium'"
                  >
                    {{ message.name }}
                  </span>
                  <UBadge
                    :color="message.status === 'NEW' ? 'primary' : 'neutral'"
                    variant="subtle"
                    size="sm"
                  >
                    {{ messageStatusLabel(message.status) }}
                  </UBadge>
                </div>

                <p class="text-muted truncate text-xs">
                  {{ message.email }}
                </p>

                <p
                  class="truncate text-sm"
                  :class="message.status === 'NEW' ? 'font-semibold text-highlighted' : 'text-toned'"
                >
                  {{ message.subject }}
                </p>

                <p class="text-muted text-xs">
                  {{ adminDate(message.createdAt, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
                </p>
              </div>

              <!-- .stop: desplegar el menu no debe abrir tambien la ficha. -->
              <div
                @click.stop
                @keydown.stop
              >
                <UDropdownMenu
                  :items="rowActions(message)"
                  :content="{ align: 'end' }"
                >
                  <UButton
                    icon="i-lucide-ellipsis"
                    color="neutral"
                    variant="ghost"
                    square
                    aria-label="Acciones del mensaje"
                  />
                </UDropdownMenu>
              </div>
            </div>
          </UCard>
        </div>

        <UTable
          class="hidden lg:block"
          :data="messages"
          :columns="columns"
          :on-select="(event, row) => openDetail(row.original)"
          aria-label="Listado de mensajes"
        >
          <template #sender-cell="{ row }">
            <div class="min-w-0">
              <p
                class="truncate text-sm"
                :class="row.original.status === 'NEW' ? 'font-semibold text-highlighted' : 'font-medium'"
              >
                {{ row.original.name }}
              </p>
              <p class="text-muted truncate text-xs">
                {{ row.original.email }}
              </p>
            </div>
          </template>

          <template #subject-cell="{ row }">
            <p
              class="truncate text-sm"
              :class="row.original.status === 'NEW' ? 'font-semibold text-highlighted' : 'text-toned'"
            >
              {{ row.original.subject }}
            </p>
          </template>

          <template #status-cell="{ row }">
            <UBadge
              :color="row.original.status === 'NEW' ? 'primary' : 'neutral'"
              variant="subtle"
              size="sm"
            >
              {{ messageStatusLabel(row.original.status) }}
            </UBadge>
          </template>

          <template #createdAt-cell="{ row }">
            <span class="text-muted text-sm whitespace-nowrap">
              {{ adminDate(row.original.createdAt, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
            </span>
          </template>

          <template #actions-cell="{ row }">
            <!-- .stop: desplegar el menú no debe abrir también la ficha. -->
            <div @click.stop>
              <UDropdownMenu
                :items="rowActions(row.original)"
                :content="{ align: 'end' }"
              >
                <UButton
                  icon="i-lucide-ellipsis"
                  color="neutral"
                  variant="ghost"
                  square
                  aria-label="Acciones del mensaje"
                />
              </UDropdownMenu>
            </div>
          </template>
        </UTable>

        <UPagination
          v-if="totalPages > 1"
          v-model:page="page"
          :total="totalElements"
          :items-per-page="PAGE_SIZE"
          class="justify-center"
        />
      </template>

      <!-- Ficha del mensaje. El cuerpo ya viene en el listado, así que no hay
           un GET /admin/messages/{id} adicional. -->
      <UModal
        v-model:open="detailOpen"
        :title="selected?.subject ?? 'Mensaje'"
      >
        <template #body>
          <div
            v-if="selected"
            class="space-y-4"
          >
            <dl class="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
              <div>
                <dt class="text-muted text-xs">
                  De
                </dt>
                <dd class="font-medium text-highlighted">
                  {{ selected.name }}
                </dd>
              </div>
              <div>
                <dt class="text-muted text-xs">
                  Correo
                </dt>
                <dd>
                  <a
                    :href="`mailto:${selected.email}`"
                    class="text-primary hover:underline"
                  >
                    {{ selected.email }}
                  </a>
                </dd>
              </div>
              <div>
                <dt class="text-muted text-xs">
                  Recibido
                </dt>
                <dd>
                  {{ adminDate(selected.createdAt, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
                </dd>
              </div>
              <div>
                <dt class="text-muted text-xs">
                  Estado
                </dt>
                <dd>{{ messageStatusLabel(selected.status) }}</dd>
              </div>
            </dl>

            <div class="border-t border-default pt-4">
              <p class="whitespace-pre-wrap break-words text-sm text-toned">
                {{ selected.body }}
              </p>
            </div>

            <p class="text-dimmed break-all text-xs">
              IP {{ selected.ip }}<span v-if="selected.userAgent"> · {{ selected.userAgent }}</span>
            </p>
          </div>
        </template>

        <template #footer>
          <div class="flex w-full flex-wrap justify-end gap-2">
            <UButton
              v-if="selected?.status === 'NEW'"
              label="Marcar como leído"
              icon="i-lucide-check"
              color="neutral"
              variant="soft"
              :loading="pendingId === selected?.id"
              @click="selected && markRead(selected)"
            />
            <UButton
              v-if="selected && selected.status !== 'ARCHIVED'"
              label="Archivar"
              icon="i-lucide-archive"
              color="neutral"
              variant="soft"
              :loading="pendingId === selected?.id"
              @click="selected && archive(selected)"
            />
            <UButton
              v-if="selected"
              label="Borrar"
              icon="i-lucide-trash-2"
              color="error"
              variant="soft"
              @click="askDelete(selected)"
            />
            <UButton
              label="Cerrar"
              color="neutral"
              variant="ghost"
              @click="detailOpen = false"
            />
          </div>
        </template>
      </UModal>

      <!-- Confirmación de borrado. -->
      <UModal
        v-model:open="deleteOpen"
        title="¿Borrar mensaje?"
        :dismissible="!deleting"
        :close="!deleting"
      >
        <template #body>
          <p
            v-if="deleteTarget"
            class="text-muted text-sm"
          >
            Se elimina el mensaje de
            <span class="font-medium text-highlighted">{{ deleteTarget.name }}</span>
            de forma permanente. Esta acción no se puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="deleting"
              @click="deleteOpen = false"
            />
            <UButton
              label="Borrar"
              icon="i-lucide-trash-2"
              color="error"
              :loading="deleting"
              @click="confirmDelete"
            />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
