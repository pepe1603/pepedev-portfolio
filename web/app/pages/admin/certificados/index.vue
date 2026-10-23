<script setup lang="ts">
import type { TableColumn, DropdownMenuItem } from '@nuxt/ui'
import { toApiError, unwrapApiError } from '~/composables/useApi'
import { CARD_INTERACTIVA } from '~/utils/cardStyles'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()

// POR QUÉ NO SE CARGA EN SSR
//
// Mismo motivo que en /admin/proyectos: el $fetch de Nuxt no reenvía las
// cookies del navegador durante el render del servidor, así que la petición
// llegaría al proxy sin sesión y respondería 401. Es contenido privado y
// volátil, sin caché de SSR que aprovechar.
const {
  data: certificates,
  status,
  error,
  refresh
} = await useAsyncData(
  'admin-certificates',
  () => api.get<AdminCertificate[]>('/certificates'),
  { server: false }
)

// El estado de error se decide por `error` y NO por su tipo: si la API no
// responde (red caída, Spring parado) el fallo no llega como ProblemDetail y
// unwrapApiError devuelve null. Sin esto la página pintaría "Todavía no hay
// certificados", que es mentira: no se pudo consultar.
const loadError = computed(() => unwrapApiError(error.value))

const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// FILTROS (client-side)
//
// GET /admin/certificates devuelve la lista COMPLETA, sin paginar (la ordena
// la API por sortOrder ASC → createdAt ASC), así que filtrar aquí es
// correcto y evita un round trip por cada tecla.
// ---------------------------------------------------------------------------
const search = ref('')
const statusFilter = ref<'todos' | CertificateStatus>('todos')
const kindFilter = ref<'todos' | CertificateKind>('todos')

const statusOptions = [
  { label: 'Todos los estados', value: 'todos' },
  { label: 'Borradores', value: 'DRAFT' },
  { label: 'Publicados', value: 'PUBLISHED' }
]

const kindOptions = [
  { label: 'Todos los tipos', value: 'todos' },
  { label: 'Certificados', value: 'CERTIFICATE' },
  { label: 'Cursos', value: 'COURSE' }
]

const visibleCertificates = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return (certificates.value ?? []).filter((certificate) => {
    if (statusFilter.value !== 'todos' && certificate.status !== statusFilter.value) {
      return false
    }
    if (kindFilter.value !== 'todos' && certificate.kind !== kindFilter.value) {
      return false
    }
    if (!needle) {
      return true
    }
    return [localizedText(certificate.title), certificate.issuer, certificateKindLabel(certificate.kind)]
      .some(field => field.toLowerCase().includes(needle))
  })
})

const filtersActive = computed(
  () => search.value.trim() !== '' || statusFilter.value !== 'todos' || kindFilter.value !== 'todos'
)

function clearFilters() {
  search.value = ''
  statusFilter.value = 'todos'
  kindFilter.value = 'todos'
}

// ---------------------------------------------------------------------------
// COLUMNAS
//
// La fecha la pinta adminDate() de utils/adminTypes.ts, que sabe que
// `issueDate` es un LocalDate ('yyyy-MM-dd') y no lo desplaza de día.
// Certificado, además, no tiene updatedAt: solo cuenta la emisión.
// ---------------------------------------------------------------------------
// ---------------------------------------------------------------------------
const columns: TableColumn<AdminCertificate>[] = [
  { id: 'title', header: 'Título' },
  { id: 'kind', header: 'Tipo' },
  { id: 'status', header: 'Estado' },
  { id: 'featured', header: 'Destacado' },
  { id: 'issueDate', header: 'Emitido' },
  { id: 'actions', header: '' }
]

// ---------------------------------------------------------------------------
// ACCIONES POR FILA
// ---------------------------------------------------------------------------
const pendingId = ref<string | null>(null)
const deleteTarget = ref<AdminCertificate | null>(null)
const deleteOpen = ref(false)
const deleting = ref(false)

function isPending(id: string): boolean {
  return pendingId.value === id
}

async function togglePublish(certificate: AdminCertificate) {
  const goingLive = certificate.status === 'DRAFT'
  pendingId.value = certificate.id
  try {
    await api.patch(`/certificates/${certificate.id}/${goingLive ? 'publish' : 'unpublish'}`)
    notify(goingLive ? 'Certificado publicado' : 'Certificado despublicado', 'success')
    await refresh()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    pendingId.value = null
  }
}

function askDelete(certificate: AdminCertificate) {
  deleteTarget.value = certificate
  deleteOpen.value = true
}

async function confirmDelete() {
  const certificate = deleteTarget.value
  if (!certificate) return

  deleting.value = true
  try {
    // DELETE responde 204 sin cuerpo. El backend, además, borra del bucket la
    // imagen si deja de estar referenciada (OrphanFileCleaner).
    await api.remove(`/certificates/${certificate.id}`)
    notify('Certificado eliminado', 'success')
    deleteOpen.value = false
    deleteTarget.value = null
    await refresh()
  } catch (err) {
    notify(toApiError(err).message, 'error')
  } finally {
    deleting.value = false
  }
}

function openCertificate(id: string) {
  return navigateTo(`/admin/certificados/${id}`)
}

function rowActions(certificate: AdminCertificate): DropdownMenuItem[][] {
  return [
    [
      {
        label: 'Editar',
        icon: 'i-lucide-pencil',
        onSelect: () => navigateTo(`/admin/certificados/${certificate.id}`)
      },
      {
        label: certificate.status === 'DRAFT' ? 'Publicar' : 'Despublicar',
        icon: certificate.status === 'DRAFT' ? 'i-lucide-eye' : 'i-lucide-eye-off',
        disabled: isPending(certificate.id),
        onSelect: () => togglePublish(certificate)
      },
      {
        label: 'Borrar',
        icon: 'i-lucide-trash-2',
        color: 'error',
        disabled: isPending(certificate.id),
        onSelect: () => askDelete(certificate)
      }
    ]
  ]
}
</script>

<template>
  <UDashboardPanel id="admin-certificados">
    <template #header>
      <UDashboardNavbar title="Certificados">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Nuevo certificado"
            icon="i-lucide-plus"
            to="/admin/certificados/nuevo"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Filtros. El buscador filtra título, emisor y tipo. -->
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Buscar por título o emisor…"
          class="w-full sm:max-w-xs"
          aria-label="Buscar certificados"
        />

        <USelect
          v-model="kindFilter"
          :items="kindOptions"
          class="w-full sm:w-44"
          aria-label="Filtrar por tipo"
        />

        <USelect
          v-model="statusFilter"
          :items="statusOptions"
          class="w-full sm:w-48"
          aria-label="Filtrar por estado"
        />

        <UButton
          v-if="filtersActive"
          label="Limpiar filtros"
          icon="i-lucide-x"
          color="neutral"
          variant="soft"
          class="self-start"
          @click="clearFilters"
        />

        <p
          v-else-if="certificates?.length"
          class="text-muted ms-auto text-sm"
        >
          {{ certificates.length }} {{ certificates.length === 1 ? 'certificado' : 'certificados' }}
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
          class="h-12 w-full"
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
              No se pudieron cargar los certificados
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

      <!-- Sin certificados todavía. -->
      <UCard
        v-else-if="!certificates?.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Todavía no hay certificados
            </p>
            <p class="text-muted text-sm">
              Se crean como borrador: no se publican hasta que lo decidas.
            </p>
          </div>
          <UButton
            label="Crear el primero"
            icon="i-lucide-plus"
            to="/admin/certificados/nuevo"
          />
        </div>
      </UCard>

      <!-- Ningún resultado de los filtros. -->
      <UCard
        v-else-if="!visibleCertificates.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Ningún certificado coincide
            </p>
            <p class="text-muted text-sm">
              Prueba con otro término o quita los filtros.
            </p>
          </div>
          <UButton
            label="Limpiar filtros"
            icon="i-lucide-x"
            color="neutral"
            variant="soft"
            @click="clearFilters"
          />
        </div>
      </UCard>

      <div
        v-else
        class="flex flex-col gap-3"
      >
        <!-- Tarjetas por debajo de lg: la tabla mide ~798 px y en pantallas
             estrechas el menu de acciones quedaba fuera de vista. -->
        <div class="flex flex-col gap-3 lg:hidden">
          <UCard
            v-for="certificate in visibleCertificates"
            :key="certificate.id"
            class="cursor-pointer"
            :ui="{
              root: CARD_INTERACTIVA,
              body: 'flex flex-col gap-3 p-4 sm:p-5'
            }"
            role="button"
            tabindex="0"
            :aria-label="`Editar certificado: ${localizedText(certificate.title) || certificate.issuer}`"
            @click="openCertificate(certificate.id)"
            @keydown.enter.prevent="openCertificate(certificate.id)"
            @keydown.space.prevent="openCertificate(certificate.id)"
          >
            <!-- Miniatura -->
            <div class="relative overflow-hidden rounded-xl bg-elevated">
              <NuxtImg
                v-if="certificate.imageUrl"
                :src="certificate.imageUrl"
                :alt="localizedText(certificate.title) || ''"
                width="960"
                height="540"
                loading="lazy"
                class="aspect-video w-full object-cover transition-transform duration-500 group-hover:scale-[1.03] motion-reduce:transition-none"
              />
              <div
                v-else
                class="flex aspect-video w-full items-center justify-center text-muted"
                aria-hidden="true"
              >
                <UIcon
                  name="i-lucide-award"
                  class="size-10"
                />
              </div>
              <div
                v-if="certificate.featured"
                class="absolute left-3 top-3"
              >
                <UBadge
                  color="primary"
                  variant="solid"
                  size="sm"
                >
                  <UIcon
                    name="i-lucide-star"
                    class="mr-1 size-3"
                  />
                  Destacado
                </UBadge>
              </div>
            </div>

            <div class="flex flex-col gap-2">
              <div class="flex items-start justify-between gap-3">
                <span class="min-w-0 text-lg font-semibold tracking-tight text-highlighted transition-colors group-hover:text-primary">
                  {{ localizedText(certificate.title) || '(sin título)' }}
                </span>
                <UIcon
                  name="i-lucide-pencil"
                  class="mt-1 size-4 shrink-0 text-muted transition-transform duration-300 group-hover:translate-x-0.5 group-hover:text-primary motion-reduce:transition-none"
                  aria-hidden="true"
                />
              </div>
              <p class="text-sm font-medium text-muted">
                {{ certificate.issuer }}
              </p>
              <div class="flex flex-wrap items-center gap-2">
                <UBadge
                  :color="certificate.status === 'PUBLISHED' ? 'success' : 'neutral'"
                  variant="subtle"
                  size="sm"
                >
                  {{ certificate.status === 'PUBLISHED' ? 'Publicado' : 'Borrador' }}
                </UBadge>
                <UBadge
                  color="neutral"
                  variant="outline"
                  size="sm"
                >
                  {{ certificateKindLabel(certificate.kind) }}
                </UBadge>
                <span class="text-muted text-xs">
                  Emitido el {{ adminDate(certificate.issueDate) }}
                </span>
              </div>
            </div>

            <div class="flex items-center gap-2 border-t border-default pt-3 text-sm font-medium text-primary">
              <span>Editar</span>
              <UIcon
                name="i-lucide-arrow-right"
                class="size-4 transition-transform group-hover:translate-x-1 motion-reduce:transition-none"
                aria-hidden="true"
              />
            </div>

            <template #footer>
              <div
                class="flex w-full justify-end"
                @click.stop
                @keydown.stop
              >
                <UDropdownMenu
                  :items="rowActions(certificate)"
                  :content="{ align: 'end' }"
                >
                  <UButton
                    label="Acciones"
                    icon="i-lucide-ellipsis"
                    color="neutral"
                    variant="soft"
                    size="sm"
                    block
                    aria-label="Acciones del certificado"
                  />
                </UDropdownMenu>
              </div>
            </template>
          </UCard>
        </div>

        <UTable
          class="hidden overflow-hidden rounded-xl border border-default lg:block [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-elevated/50"
          :data="visibleCertificates"
          :columns="columns"
          aria-label="Listado de certificados"
        >
          <template #title-cell="{ row }">
            <div class="min-w-0">
              <p class="truncate font-medium text-highlighted">
                {{ localizedText(row.original.title) || '(sin título)' }}
              </p>
              <p class="text-muted truncate text-xs">
                {{ row.original.issuer }}
              </p>
            </div>
          </template>

          <template #kind-cell="{ row }">
            <UBadge
              color="neutral"
              variant="outline"
              size="sm"
            >
              {{ certificateKindLabel(row.original.kind) }}
            </UBadge>
          </template>

          <template #status-cell="{ row }">
            <UBadge
              :color="row.original.status === 'PUBLISHED' ? 'success' : 'neutral'"
              variant="subtle"
              size="sm"
            >
              {{ row.original.status === 'PUBLISHED' ? 'Publicado' : 'Borrador' }}
            </UBadge>
          </template>

          <template #featured-cell="{ row }">
            <UIcon
              v-if="row.original.featured"
              name="i-lucide-star"
              class="text-warning size-4"
              aria-label="Destacado"
            />
            <span
              v-else
              class="text-dimmed text-xs"
              aria-label="No destacado"
            >—</span>
          </template>

          <template #issueDate-cell="{ row }">
            <span class="text-muted text-sm">
              {{ adminDate(row.original.issueDate) }}
            </span>
          </template>

          <template #actions-cell="{ row }">
            <UDropdownMenu
              :items="rowActions(row.original)"
              :content="{ align: 'end' }"
            >
              <UButton
                icon="i-lucide-ellipsis"
                color="neutral"
                variant="ghost"
                square
                aria-label="Acciones del certificado"
              />
            </UDropdownMenu>
          </template>
        </UTable>
      </div>

      <!-- Borrado. El modal se cierra con ESC o clic fuera. -->
      <UModal
        v-model:open="deleteOpen"
        title="¿Borrar certificado?"
        :dismissible="!deleting"
        :close="!deleting"
      >
        <template #body>
          <p
            v-if="deleteTarget"
            class="text-muted text-sm"
          >
            Se eliminará
            <span class="font-medium text-highlighted">{{ localizedText(deleteTarget.title) }}</span>
            de forma permanente, junto con su imagen si nadie más la referencia.
            Esta acción no se puede deshacer.
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
