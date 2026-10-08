<script setup lang="ts">
// ======================================================================
// /certificados — listado público
//
// GET /public/certificates?lang=... acepta filtros combinables (AND) en
// el SERVIDOR: kind ('certificate' | 'course', en minúsculas) e issuer
// (igualdad exacta). A diferencia de /proyectos, aquí el filtro viaja en
// la petición (docs/API.md), así que cada cambio re-lanza el fetch: sin
// `watch` sobre los refs, la lista seguiría filtrada con los parámetros
// anteriores.
//
// Los chips de kind solo pueden llevar los valores válidos del contrato:
// un kind distinto devuelve 400, y aquí no hay manera de enviarlo.
//
// Un filtro sin resultados NO es error: la API devuelve `200 []`, y la
// UI lo distingue de un fallo real por el `error` de useAsyncData.
// ======================================================================
const api = useApi()
const lang = useLang()

// null = sin filtro ("Todos").
const kind = ref<'certificate' | 'course' | null>(null)
const issuer = ref<string | null>(null)

const { data: certificates, error } = await useAsyncData(
  'certificates-list',
  () => {
    const params: Record<string, string> = {}

    if (kind.value) params.kind = kind.value
    if (issuer.value) params.issuer = issuer.value

    return api.getPublic<CertificatePublicDTO[]>('/public/certificates', params)
  },
  { watch: [lang, kind, issuer] }
)

// Las etiquetas de la interfaz siguen en español (decisión de esta fase;
// la traducción completa es la Fase 4), pero los VALORES del filtro son
// los del contrato.
const kindLabels: Record<string, string> = {
  certificate: 'Certificado',
  course: 'Curso'
}

// Los emisores salen de los resultados actuales: si kind ya está activo,
// solo aparecen emisores de ese tipo. "Todos" siempre está a un click,
// que es como se sale de un filtro sin resultados.
const issuerOptions = computed(() => {
  const set = new Set<string>()

  for (const certificate of certificates.value ?? []) {
    set.add(certificate.issuer)
  }

  return [...set].sort((a, b) => a.localeCompare(b))
})

function toggleKind(value: 'certificate' | 'course') {
  kind.value = kind.value === value ? null : value
}

function toggleIssuer(value: string) {
  issuer.value = issuer.value === value ? null : value
}

// 'yyyy-MM-dd' del backend a fecha legible en el idioma activo.
function formatDate(iso: string): string {
  const locale = lang.value === 'en' ? 'en' : 'es'

  return new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(`${iso}T00:00:00`))
}

// Línea de emisor + fechas. Se compone en el script para no partir el
// texto en trozos de template con espacios colgando.
function metaLine(certificate: CertificatePublicDTO): string {
  const parts = [certificate.issuer]

  if (certificate.issueDate) parts.push(formatDate(certificate.issueDate))
  if (certificate.expiryDate) parts.push(`hasta ${formatDate(certificate.expiryDate)}`)

  return parts.join(' · ')
}

useSeoMeta({
  title: 'Certificados — pepedev',
  description: 'Certificados y cursos con su emisor, fecha y credencial.'
})
</script>

<template>
  <UPage>
    <UPageHeader
      title="Certificados"
      description="Certificados y cursos, filtrables por tipo y emisor."
    />

    <UPageBody>
      <UAlert
        v-if="error"
        color="error"
        variant="subtle"
        icon="i-lucide-cloud-off"
        title="No se pudieron cargar los certificados"
        description="La API no está respondiendo. Inténtalo de nuevo en unos segundos."
        class="my-8"
      />

      <template v-else>
        <!-- Filtro por tipo: los tres valores caben a la vista, no hace
             falta un desplegable. -->
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-muted text-sm">Tipo:</span>

          <UButton
            label="Todos"
            size="xs"
            :color="!kind ? 'primary' : 'neutral'"
            :variant="!kind ? 'soft' : 'ghost'"
            @click="kind = null"
          />

          <UButton
            v-for="(label, value) in kindLabels"
            :key="value"
            :label="label"
            size="xs"
            :color="kind === value ? 'primary' : 'neutral'"
            :variant="kind === value ? 'soft' : 'ghost'"
            @click="toggleKind(value as 'certificate' | 'course')"
          />
        </div>

        <div
          v-if="issuerOptions.length"
          class="mt-3 flex flex-wrap items-center gap-2"
        >
          <span class="text-muted text-sm">Emisor:</span>

          <UButton
            label="Todos"
            size="xs"
            :color="!issuer ? 'primary' : 'neutral'"
            :variant="!issuer ? 'soft' : 'ghost'"
            @click="issuer = null"
          />

          <UButton
            v-for="name in issuerOptions"
            :key="name"
            :label="name"
            size="xs"
            :color="issuer === name ? 'primary' : 'neutral'"
            :variant="issuer === name ? 'soft' : 'ghost'"
            @click="toggleIssuer(name)"
          />
        </div>

        <UAlert
          v-if="!certificates?.length"
          color="neutral"
          variant="subtle"
          icon="i-lucide-award"
          title="No hay certificados con esos filtros"
          description="Prueba a quitar algún filtro."
          class="my-8"
        />

        <div
          v-else
          class="grid gap-6 py-8 sm:grid-cols-2 lg:grid-cols-3"
        >
          <UCard
            v-for="certificate in certificates"
            :key="certificate.title"
            class="flex flex-col gap-3"
          >
            <NuxtImg
              v-if="certificate.imageUrl"
              :src="certificate.imageUrl"
              :alt="certificate.title"
              width="640"
              height="360"
              class="aspect-video w-full rounded-md object-cover"
            />

            <div class="flex items-start justify-between gap-2">
              <h2 class="font-semibold">
                {{ certificate.title }}
              </h2>

              <UBadge
                color="neutral"
                variant="subtle"
                size="sm"
              >
                {{ kindLabels[certificate.kind] ?? certificate.kind }}
              </UBadge>
            </div>

            <p class="text-muted text-sm">
              {{ metaLine(certificate) }}
            </p>

            <UButton
              v-if="certificate.credentialUrl"
              :href="certificate.credentialUrl"
              target="_blank"
              label="Ver credencial"
              icon="i-lucide-external-link"
              size="sm"
              color="neutral"
              variant="soft"
              class="mt-auto self-start"
            />
          </UCard>
        </div>
      </template>
    </UPageBody>
  </UPage>
</template>
