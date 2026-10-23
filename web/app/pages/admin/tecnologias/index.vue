<script setup lang="ts">
// ======================================================================
// TECNOLOGÍAS (STACK)
//
// Gestiona el array `skills` del perfil: crear, editar, borrar y
// reordenar. No hay un CRUD propio en la API: skills vive dentro del
// perfil singleton, así que CADA operación es un PUT /admin/profile
// completo. Ver saveSkills, que es donde se explica qué se reenvía.
//
// El orden importa: la web pública pinta las tecnologías agrupadas por
// categoría y, dentro de cada grupo, en el orden del array. De ahí los
// botones de mover arriba/abajo, que actúan sobre el array completo y no
// sobre la vista filtrada.
// ======================================================================
import * as z from 'zod'
import type { TableColumn, DropdownMenuItem, FormSubmitEvent } from '@nuxt/ui'
import { toApiError, unwrapApiError } from '~/composables/useApi'
import { TECH_ICON_FALLBACK, techIcon } from '~/utils/techIcons'
import { CARD_INTERACTIVA } from '~/utils/cardStyles'

definePageMeta({ layout: 'dashboard' })

const api = useAdminApi()
const { notify } = useAppNotify()

// Mismo motivo que en /admin/proyectos y /admin/certificados: el $fetch de
// Nuxt no reenvía las cookies del navegador durante el render del servidor,
// así que SSR llegaría al proxy sin sesión y respondería 401.
const {
  data: profile,
  status,
  error,
  refresh
} = await useAsyncData(
  'admin-profile',
  () => api.get<AdminProfile>('/profile'),
  { server: false }
)

// El estado de error se decide por `error` y NO por su tipo: si la API no
// responde (red caída, Spring parado) el fallo no llega como ProblemDetail
// y unwrapApiError devuelve null.
const loadError = computed(() => unwrapApiError(error.value))

const loadErrorMessage = computed(
  () => loadError.value?.problem.detail ?? loadError.value?.message ?? 'No se pudo conectar con el servidor'
)

// ---------------------------------------------------------------------------
// FILTROS (client-side)
//
// GET /admin/profile devuelve el perfil entero de una vez, así que filtrar
// aquí evita un round trip por cada tecla, igual que en el resto del panel.
// ---------------------------------------------------------------------------
const search = ref('')
const categoryFilter = ref('todos')

// Las categorías salen de los datos guardados: no hay endpoint de
// categorías y el texto es libre. 'Sin categoría' (vacío en la base) se
// muestra con su nombre público, que la web resuelve a "Otros".
const categoryOptions = computed(() => {
  const categories = new Set((profile.value?.skills ?? []).map(skill => skill.category.trim()))

  return [
    { label: 'Todas las categorías', value: 'todos' },
    ...[...categories]
      .sort((a, b) => a.localeCompare(b, 'es'))
      .map(category => ({ label: category || 'Sin categoría', value: category }))
  ]
})

const visibleSkills = computed(() => {
  const needle = search.value.trim().toLowerCase()

  return (profile.value?.skills ?? []).filter((skill) => {
    if (categoryFilter.value !== 'todos' && skill.category.trim() !== categoryFilter.value) {
      return false
    }
    if (!needle) {
      return true
    }
    return [skill.name, skill.category, skill.level ?? '']
      .some(field => field.toLowerCase().includes(needle))
  })
})

const filtersActive = computed(
  () => search.value.trim() !== '' || categoryFilter.value !== 'todos'
)

function clearFilters() {
  search.value = ''
  categoryFilter.value = 'todos'
}

/** "1 tecnología" / "12 tecnologías". */
function skillsLabel(count: number): string {
  return `${count} ${count === 1 ? 'tecnología' : 'tecnologías'}`
}

// ---------------------------------------------------------------------------
// GUARDADO
//
// PUT /admin/profile reemplaza el perfil ENTERO, no hace merge de lo que
// llega: editar una tecnología reenvía también la biografía, las URLs, el
// CV y las experiencias tal cual se leyeron. Si no, la siguiente operación
// sobre el stack borraría todo lo demás.
//
// fullName (@NotBlank) y headline/bio (@LocalizedNonBlank) son
// obligatorios en el request aunque solo se quiera tocar el stack, así que
// se comprueban antes de mandar nada: el 400 que devolvería la API sería
// exactamente igual de específico, pero saldría dentro de un formulario de
// tecnologías y no se entendería.
// ---------------------------------------------------------------------------
const MAX_SKILLS = 50 // @Size(max = 50) en ProfileRequest.java
const saving = ref(false)

function assertProfileComplete(current: AdminProfile): void {
  const missing: string[] = []
  if (!current.fullName.trim()) missing.push('el nombre completo')
  if (!hasLocalizedText(current.headline)) missing.push('el titular bilingüe')
  if (!hasLocalizedText(current.bio)) missing.push('la biografía bilingüe')

  if (missing.length) {
    throw new Error(
      `No se puede guardar: al perfil le falta ${missing.join(' y ')}, y la API lo exige en cualquier edición del perfil.`
    )
  }
}

async function saveSkills(skills: AdminSkill[]): Promise<void> {
  const current = profile.value
  if (!current) {
    throw new Error('No hay perfil cargado todavía')
  }

  assertProfileComplete(current)

  const body: ProfileRequest = {
    fullName: current.fullName,
    headline: current.headline ?? {},
    bio: current.bio ?? {},
    location: current.location,
    githubUrl: current.githubUrl,
    linkedinUrl: current.linkedinUrl,
    emailPublic: current.emailPublic,
    websiteUrl: current.websiteUrl,
    cvUrlEs: current.cvUrlEs,
    cvUrlEn: current.cvUrlEn,
    avatarUrl: current.avatarUrl,
    skills,
    experiences: current.experiences
  }

  // La API devuelve el perfil ya guardado: se usa como estado nuevo en vez
  // de volver a pedirlo, y con él el panel se queda al día sin otro GET.
  profile.value = await api.put<AdminProfile>('/profile', body)
}

/**
 * Mensaje para avisar de un fallo. El `detail` del 400 de validación es
 * genérico ("Validación fallida: se encontraron 1 errores") y el motivo
 * real va en errors[], así que se mira primero; el resto de errores (red,
 * 500) solo tienen detail.
 */
function apiMessage(error: unknown): string {
  const problem = toApiError(error).problem
  return problem.errors?.[0]?.message ?? problem.detail ?? 'No se pudieron guardar los cambios'
}

/**
 * Aplica un cambio sobre skills y lo persiste. Devuelve true si se guardó,
 * para que quien llame sepa si debe cerrar su modal.
 */
async function persistSkills(skills: AdminSkill[], success: string): Promise<boolean> {
  saving.value = true
  try {
    await saveSkills(skills)
    notify(success, 'success')
    return true
  } catch (err) {
    notify(apiMessage(err), 'error')
    return false
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// REORDENAR
//
// Se mueve dentro del array COMPLETO, nunca del visible: el orden que pinta
// la web es el del array y la vista filtrada es solo una ventana sobre él.
// Con filtros activos el movimiento se deshabilita, porque en una tabla
// filtrada el cambio se ve a medias y no se entiende de dónde a dónde ha
// saltado la fila.
// ---------------------------------------------------------------------------
function canMove(skill: AdminSkill, step: -1 | 1): boolean {
  if (filtersActive.value || saving.value) return false
  const skills = profile.value?.skills ?? []
  const index = skills.indexOf(skill)
  return index >= 0 && index + step >= 0 && index + step < skills.length
}

function moveSkill(skill: AdminSkill, step: -1 | 1): Promise<boolean> {
  const skills = profile.value?.skills ?? []
  const index = skills.indexOf(skill)
  // El índice sale del array vivo: si entre comprobar y mover cambió el
  // perfil, no se mueve nada en vez de mover algo que no es.
  const entry = index >= 0 ? skills[index] : undefined
  if (!entry || !canMove(skill, step)) {
    return Promise.resolve(false)
  }

  const next = [...skills]
  next.splice(index, 1)
  next.splice(index + step, 0, entry)

  return persistSkills(next, 'Orden actualizado')
}

// ---------------------------------------------------------------------------
// ALTA Y EDICIÓN (modal)
//
// El formulario no toca la API: manda el resultado al guardado compartido.
// La categoría es texto libre porque la API no la valida, pero se avisa de
// que la web agrupa por texto EXACTO, así que "backend" y "Backend" serían
// dos grupos distintos en la portada.
// ---------------------------------------------------------------------------
const formOpen = ref(false)
const editing = ref<AdminSkill | null>(null)
const formAlert = ref('')

// Tipado estructural de lo único que se usa de UForm: @nuxt/ui no exporta
// el componente, así que no se puede escribir InstanceType<typeof UForm>.
const formRef = ref<{ submit: () => Promise<void> } | null>(null)

const state = reactive({
  name: '',
  category: '',
  level: ''
})

const categoryHelp = computed(() => {
  const inUse = categoryOptions.value
    .filter(option => option.value !== 'todos')
    .map(option => option.label)
    .join(', ')

  return inUse
    ? `La web agrupa por texto exacto; reutiliza las existentes para no partir un grupo en dos: ${inUse}.`
    : 'La web agrupa por categoría. Déjala vacía para agruparla en «Otros».'
})

// ---------------------------------------------------------------------------
// AVISO SOBRE EL ICONO
//
// El icono de una tecnología se resuelve por el NOMBRE: exacto, y si no, sin la
// versión y por partes de un nombre compuesto, y por último buscando una
// tecnología conocida como palabra completa dentro del nombre (el orden exacto
// está en techIcon, utils/techIcons.ts). Si no aparece ninguna, se pinta un
// icono genérico. Conviene avisarlo aquí, donde se escribe el nombre.
//
// Los ejemplos salen de la tabla y del resolutor reales, no de una lista escrita
// a mano, y por eso no se desincronizan: un nombre que hoy no resuelve no se
// muestra como ejemplo.
// ---------------------------------------------------------------------------
const ejemploIconos = ['Vue / Nuxt', 'Git / GitHub', 'TypeScript', 'Spring Boot', 'PostgreSQL', 'Docker']

const ejemploIconosTexto = computed(() =>
  ejemploIconos.filter(nombre => techIcon(nombre) !== TECH_ICON_FALLBACK).join(', ')
)

function openCreate() {
  editing.value = null
  state.name = ''
  state.category = ''
  state.level = ''
  formAlert.value = ''
  formOpen.value = true
}

function openEdit(skill: AdminSkill) {
  editing.value = skill
  state.name = skill.name
  state.category = skill.category
  state.level = skill.level ?? ''
  formAlert.value = ''
  formOpen.value = true
}

// `trim()` antes de `min(1)`: un nombre de solo espacios no es un nombre,
// y sin esto pasaría la validación y se guardaría vacío (la API no tiene
// ninguna restricción sobre los campos del skill).
const schema = z
  .object({
    name: z.string('El nombre es obligatorio').trim().min(1, 'El nombre es obligatorio'),
    category: z.string().trim(),
    level: z.string().trim()
  })
  // Duplicado por nombre + categoría, sin distinguir mayúsculas: en la web
  // serían dos tarjetas idénticas seguidas. Al editar se excluye la propia
  // fila.
  .superRefine((value, ctx) => {
    const duplicated = (profile.value?.skills ?? [])
      .filter(skill => skill !== editing.value)
      .some(skill => skill.name.trim().toLowerCase() === value.name.toLowerCase()
        && skill.category.trim().toLowerCase() === value.category.toLowerCase())

    if (duplicated) {
      ctx.addIssue({
        code: 'custom',
        path: ['name'],
        message: 'Ya hay una tecnología con ese nombre en esa categoría'
      })
    }
  })

type SkillSchema = z.input<typeof schema>

function toSkill(): AdminSkill {
  return {
    name: state.name.trim(),
    category: state.category.trim(),
    // Vacío = sin nivel: la base lo guarda null y la web lo oculta.
    level: state.level.trim() || null
  }
}

async function onSubmit(_event: FormSubmitEvent<SkillSchema>) {
  formAlert.value = ''
  const skill = toSkill()
  const current = profile.value?.skills ?? []

  // Identidad, no igualdad: al editar se sustituye la fila de esta fila y
  // no cualquier otra técnica idéntica.
  const skills = editing.value
    ? current.map(entry => (entry === editing.value ? skill : entry))
    : [...current, skill]

  saving.value = true
  try {
    await saveSkills(skills)
    notify(editing.value ? 'Cambios guardados' : 'Tecnología añadida', 'success')
    formOpen.value = false
  } catch (err) {
    formAlert.value = apiMessage(err)
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// BORRADO (modal de confirmación)
// ---------------------------------------------------------------------------
const deleteTarget = ref<AdminSkill | null>(null)
const deleteOpen = ref(false)

function askDelete(skill: AdminSkill) {
  deleteTarget.value = skill
  deleteOpen.value = true
}

async function confirmDelete() {
  const target = deleteTarget.value
  if (!target) return

  // Identidad, no igualdad: si hay dos tecnologías idénticas solo se borra
  // la de esta fila.
  const rest = (profile.value?.skills ?? []).filter(skill => skill !== target)

  if (await persistSkills(rest, 'Tecnología eliminada')) {
    deleteOpen.value = false
    deleteTarget.value = null
  }
}

// ---------------------------------------------------------------------------
// TABLA
// ---------------------------------------------------------------------------
const columns: TableColumn<AdminSkill>[] = [
  { id: 'name', header: 'Nombre' },
  { id: 'category', header: 'Categoría' },
  { id: 'level', header: 'Nivel' },
  { id: 'actions', header: '' }
]

function rowActions(skill: AdminSkill): DropdownMenuItem[][] {
  return [
    [
      {
        label: 'Editar',
        icon: 'i-lucide-pencil',
        disabled: saving.value,
        onSelect: () => openEdit(skill)
      },
      {
        label: 'Mover arriba',
        icon: 'i-lucide-arrow-up',
        disabled: !canMove(skill, -1),
        onSelect: () => moveSkill(skill, -1)
      },
      {
        label: 'Mover abajo',
        icon: 'i-lucide-arrow-down',
        disabled: !canMove(skill, 1),
        onSelect: () => moveSkill(skill, 1)
      },
      {
        label: 'Borrar',
        icon: 'i-lucide-trash-2',
        color: 'error',
        disabled: saving.value,
        onSelect: () => askDelete(skill)
      }
    ]
  ]
}

// Con el tope de la API alcanzado no se puede añadir nada más, así que el
// botón se apaga en vez de dejar que el formulario se niegue después.
const atLimit = computed(() => (profile.value?.skills.length ?? 0) >= MAX_SKILLS)
</script>

<template>
  <UDashboardPanel id="admin-tecnologias">
    <template #header>
      <UDashboardNavbar title="Tecnologías">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #right>
          <ColorModeToggle />
          <UButton
            label="Añadir tecnología"
            icon="i-lucide-plus"
            :disabled="atLimit"
            :title="atLimit ? `El perfil ya tiene ${MAX_SKILLS} tecnologías, el máximo de la API` : undefined"
            @click="openCreate"
          />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <!-- Qué se ve con esto y desde cuándo se puede tocar. -->
      <p
        v-if="status === 'success'"
        class="text-muted text-sm"
      >
        Se muestran agrupadas por categoría y en este mismo orden en la web pública. Cada cambio se guarda
        al instante.
      </p>

      <!-- Filtros. El buscador filtra nombre, categoría y nivel. -->
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <UInput
          v-model="search"
          icon="i-lucide-search"
          placeholder="Buscar tecnología…"
          class="w-full sm:max-w-xs"
          aria-label="Buscar tecnologías"
        />

        <USelect
          v-model="categoryFilter"
          :items="categoryOptions"
          class="w-full sm:w-56"
          aria-label="Filtrar por categoría"
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
          v-else-if="profile?.skills.length"
          class="text-muted ms-auto text-sm"
        >
          {{ skillsLabel(profile.skills.length) }}
        </p>
      </div>

      <!-- Aviso del tope: la API no admite más de 50 skills. -->
      <UAlert
        v-if="status === 'success' && atLimit"
        color="warning"
        variant="subtle"
        icon="i-lucide-triangle-alert"
        title="Stack al tope"
        :description="`Este perfil ya tiene las ${MAX_SKILLS} tecnologías que admite la API. Para añadir otra, borra antes una.`"
      />

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
              No se pudieron cargar las tecnologías
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

      <!-- Sin tecnologías todavía. -->
      <UCard
        v-else-if="!profile?.skills.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Todavía no hay tecnologías
            </p>
            <p class="text-muted text-sm">
              Se agrupan por categoría en la sección de habilidades de la web pública.
            </p>
          </div>
          <UButton
            label="Añadir la primera"
            icon="i-lucide-plus"
            @click="openCreate"
          />
        </div>
      </UCard>

      <!-- Ningún resultado de los filtros. -->
      <UCard
        v-else-if="!visibleSkills.length"
        variant="subtle"
      >
        <div class="flex flex-col items-start gap-3">
          <div>
            <p class="font-medium text-highlighted">
              Ninguna tecnología coincide
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
        <!-- Tarjetas por debajo de lg, para que el panel se vea igual en todas
             las paginas con listado. Esta tabla cabe a 390 px, pero se
             uniforma con proyectos/certificados/mensajes: mismas tarjetas,
             mismas acciones abajo. -->
        <div class="flex flex-col gap-3 lg:hidden">
          <UCard
            v-for="skill in visibleSkills"
            :key="`${skill.name}|${skill.category}`"
            class="cursor-pointer"
            :ui="{ root: CARD_INTERACTIVA, body: 'flex flex-col gap-2' }"
            role="button"
            tabindex="0"
            :aria-label="`Editar ${skill.name}`"
            @click="openEdit(skill)"
            @keydown.enter.prevent="openEdit(skill)"
            @keydown.space.prevent="openEdit(skill)"
          >
            <div class="flex flex-col gap-2">
              <div class="flex items-start gap-3">
                <div class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-elevated">
                  <UIcon
                    :name="techIcon(skill.name)"
                    class="size-5"
                    aria-hidden="true"
                  />
                </div>
                <div class="flex min-w-0 flex-1 flex-col gap-2">
                  <span class="font-medium text-highlighted transition-colors group-hover:text-primary">
                    {{ skill.name }}
                  </span>

                  <div class="flex flex-wrap items-center gap-2">
                    <UBadge
                      color="neutral"
                      variant="outline"
                      size="sm"
                    >
                      {{ skill.category || 'Sin categoría' }}
                    </UBadge>
                    <span
                      v-if="skill.level"
                      class="text-muted text-sm"
                    >
                      {{ skill.level }}
                    </span>
                  </div>
                </div>
                <UIcon
                  name="i-lucide-pencil"
                  class="mt-1 size-4 shrink-0 text-muted transition-transform duration-300 group-hover:translate-x-0.5 group-hover:text-primary motion-reduce:transition-none"
                  aria-hidden="true"
                />
              </div>
            </div>

            <template #footer>
              <div
                class="flex w-full justify-end"
                @click.stop
                @keydown.stop
              >
                <UDropdownMenu
                  :items="rowActions(skill)"
                  :content="{ align: 'end' }"
                >
                  <UButton
                    label="Acciones"
                    icon="i-lucide-ellipsis"
                    color="neutral"
                    variant="soft"
                    size="sm"
                    block
                    aria-label="Acciones de la tecnología"
                  />
                </UDropdownMenu>
              </div>
            </template>
          </UCard>
        </div>

        <UTable
          class="hidden overflow-hidden rounded-xl border border-default lg:block [&_tbody_tr]:transition-colors [&_tbody_tr:hover]:bg-elevated/50"
          :data="visibleSkills"
          :columns="columns"
          aria-label="Listado de tecnologías"
        >
          <template #name-cell="{ row }">
            <span class="font-medium text-highlighted">
              {{ row.original.name }}
            </span>
          </template>

          <template #category-cell="{ row }">
            <UBadge
              color="neutral"
              variant="outline"
              size="sm"
            >
              {{ row.original.category || 'Sin categoría' }}
            </UBadge>
          </template>

          <template #level-cell="{ row }">
            <span
              v-if="row.original.level"
              class="text-muted text-sm"
            >
              {{ row.original.level }}
            </span>
            <span
              v-else
              class="text-dimmed text-xs"
              aria-label="Sin nivel"
            >—</span>
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
                aria-label="Acciones de la tecnología"
              />
            </UDropdownMenu>
          </template>
        </UTable>
      </div>

      <!-- Alta y edición. Se cierra con ESC o clic fuera si no hay un guardado en curso. -->
      <UModal
        v-model:open="formOpen"
        :title="editing ? 'Editar tecnología' : 'Añadir tecnología'"
        :dismissible="!saving"
        :close="!saving"
      >
        <template #body>
          <UForm
            ref="formRef"
            :schema="schema"
            :state="state"
            class="flex flex-col gap-4"
            @submit="onSubmit"
          >
            <UAlert
              v-if="formAlert"
              color="error"
              variant="subtle"
              icon="i-lucide-circle-x"
              :description="formAlert"
            />

            <!-- Cómo se resuelve el icono: evita nombres que caen al genérico. -->
            <UAlert
              color="info"
              variant="subtle"
              icon="i-lucide-info"
              title="El icono se busca por el nombre"
            >
              <template #description>
                <p>
                  Se busca por el nombre: primero exacto y, si no coincide, ignorando la versión
                  («Next.js 15») y los separadores con que se unen dos tecnologías («Vue / Nuxt»
                  usa el icono de Vue). También se reconoce una tecnología conocida dentro del
                  nombre («React Native» usa el de React). Si no aparece ninguna, se pinta un
                  icono genérico.
                </p>
                <p
                  v-if="ejemploIconosTexto"
                  class="mt-2"
                >
                  Reconocidos, por ejemplo: <strong>{{ ejemploIconosTexto }}</strong>.
                </p>
              </template>
              <template #actions>
                <UButton
                  label="Ver todos los iconos reconocidos"
                  icon="i-lucide-swatch-book"
                  color="info"
                  variant="link"
                  size="sm"
                  to="/admin/tecnologias/iconos"
                />
              </template>
            </UAlert>

            <UFormField
              label="Nombre"
              name="name"
              required
              help="Lo que se muestra en la web, p. ej. «Spring Boot» o «Vue / Nuxt»."
            >
              <UInput
                v-model="state.name"
                placeholder="Spring Boot"
                autofocus
                class="w-full"
              />
            </UFormField>

            <UFormField
              label="Categoría"
              name="category"
              :help="categoryHelp"
            >
              <UInput
                v-model="state.category"
                placeholder="Backend"
                class="w-full"
              />
            </UFormField>

            <UFormField
              label="Nivel"
              name="level"
              help="Opcional: se pinta como etiqueta junto al nombre. Déjalo vacío si no quieres mostrar nivel."
            >
              <UInput
                v-model="state.level"
                placeholder="Medio"
                class="w-full"
              />
            </UFormField>
          </UForm>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="saving"
              @click="formOpen = false"
            />
            <UButton
              :label="editing ? 'Guardar cambios' : 'Añadir tecnología'"
              :icon="editing ? 'i-lucide-save' : 'i-lucide-plus'"
              :loading="saving"
              @click="formRef?.submit()"
            />
          </div>
        </template>
      </UModal>

      <!-- Borrado. El modal se cierra con ESC o clic fuera. -->
      <UModal
        v-model:open="deleteOpen"
        title="¿Borrar tecnología?"
        :dismissible="!saving"
        :close="!saving"
      >
        <template #body>
          <p
            v-if="deleteTarget"
            class="text-muted text-sm"
          >
            Se eliminará
            <span class="font-medium text-highlighted">{{ deleteTarget.name }}</span>
            del stack y dejará de verse en la web pública. El resto del perfil no se toca. Esta acción no se
            puede deshacer.
          </p>
        </template>

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <UButton
              label="Cancelar"
              color="neutral"
              variant="soft"
              :disabled="saving"
              @click="deleteOpen = false"
            />
            <UButton
              label="Borrar"
              icon="i-lucide-trash-2"
              color="error"
              :loading="saving"
              @click="confirmDelete"
            />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
