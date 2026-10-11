<script setup lang="ts">
// ======================================================================
// PERFIL: avatar, bio, enlaces sociales y descarga del CV
//
// Sección nueva (la plantilla no tenía equivalente); sigue el patrón de
// las sections/ de la plantilla: recibe datos por props y no decide de
// dónde salen. Quien decide eso es pages/index.vue.
//
// --------------------------------------------------------------------------
// POR QUE LAS URLs SALEN DEL DTO Y NO SE MONTAN A MANO
//
// avatarUrl, cvUrlEs y cvUrlEn ya son URLs absolutas que la API construye
// sobre APP_STORAGE_PUBLIC_URL (su propio host, con /files). Montar
// `${apiBase}/files/${nombre}` aqui duplicaría una regla que vive en el
// backend y saltaría el día que el storage cambie de host.
//
// Los campos pueden venir null (perfil sin avatar, CV sin subir): sin
// este check la UI enseñaría un boton roto.
// ======================================================================
import type { ProfilePublicDTO } from '~/utils/types'

const props = defineProps<{
  profile: ProfilePublicDTO
}>()

const lang = useLang()

// El CV sigue el idioma activo de la cabecera. No se llama a
// GET /public/cv/{lang} porque la respuesta es un 302 a esta misma URL:
// el boton ya apunta al destino y el browser sigue el redirect solo.
const cvUrl = computed(() => (lang.value === 'en' ? props.profile.cvUrlEn : props.profile.cvUrlEs))

interface SocialLink {
  label: string
  icon: string
  to: string
  external: boolean
}

// Solo aparecen los enlaces que el profile trae rellenos: un perfil sin
// LinkedIn no enseña un boton de LinkedIn.
const socialLinks = computed<SocialLink[]>(() => {
  const links: SocialLink[] = []

  if (props.profile.githubUrl) {
    links.push({ label: 'GitHub', icon: 'i-simple-icons-github', to: props.profile.githubUrl, external: true })
  }

  if (props.profile.linkedinUrl) {
    links.push({ label: 'LinkedIn', icon: 'i-simple-icons-linkedin', to: props.profile.linkedinUrl, external: true })
  }

  if (props.profile.websiteUrl) {
    links.push({ label: 'Web', icon: 'i-lucide-globe', to: props.profile.websiteUrl, external: true })
  }

  if (props.profile.emailPublic) {
    links.push({ label: 'Email', icon: 'i-lucide-mail', to: `mailto:${props.profile.emailPublic}`, external: false })
  }

  return links
})
</script>

<template>
  <UPageSection
    id="perfil"
    headline="CONÓCEME"
    title="Sobre mí"
    description="Un poco sobre quién soy y cómo trabajo."
    class="scroll-mt-24"
  >
    <div
      class="grid items-center gap-8 md:grid-cols-[220px_minmax(0,1fr)] md:gap-12"
    >
      <!-- Avatar -->
      <div class="flex justify-center md:justify-start">
        <div
          class="relative size-48 overflow-hidden rounded-2xl
                 bg-elevated ring-1 ring-default
                 md:size-56"
        >
          <NuxtImg
            v-if="profile.avatarUrl"
            :src="profile.avatarUrl"
            :alt="`Foto de ${profile.fullName}`"
            width="224"
            height="224"
            sizes="192px md:224px"
            format="webp"
            class="size-full object-cover"
          />

          <div
            v-else
            class="flex size-full items-center justify-center"
          >
            <UIcon
              name="i-lucide-user-round"
              class="size-20 text-muted"
            />
          </div>
        </div>
      </div>

      <!-- Información -->
      <div class="flex min-w-0 flex-col gap-5">
        <div class="space-y-3">
          <h3 class="text-2xl font-semibold tracking-tight">
            {{ profile.fullName }}
          </h3>

          <p class="text-muted leading-7 whitespace-pre-line">
            {{ profile.bio }}
          </p>
        </div>

        <div
          v-if="profile.location"
          class="flex items-center gap-2 text-sm text-muted"
        >
          <UIcon
            name="i-lucide-map-pin"
            class="size-4 shrink-0"
          />
          <span>{{ profile.location }}</span>
        </div>

        <!-- Enlaces sociales -->
        <div
          v-if="socialLinks.length"
          class="flex flex-wrap gap-2"
        >
          <UButton
            v-for="link in socialLinks"
            :key="link.label"
            :label="link.label"
            :icon="link.icon"
            :to="link.to"
            :target="link.external ? '_blank' : undefined"
            :rel="link.external ? 'noopener noreferrer' : undefined"
            color="neutral"
            variant="outline"
            size="sm"
          />
        </div>

        <!-- CV -->
        <div v-if="cvUrl">
          <UButton
            :href="cvUrl"
            target="_blank"
            rel="noopener noreferrer"
            label="Descargar CV"
            icon="i-lucide-file-down"
            trailing-icon="i-lucide-arrow-up-right"
            size="lg"
          />
        </div>
      </div>
    </div>
  </UPageSection>
</template>
