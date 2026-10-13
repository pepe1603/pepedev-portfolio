<script setup lang="ts">
import { z } from 'zod'

interface ContactLink {
  label: string
  to: string
  icon: string
}

interface Props {
  links?: ContactLink[]
}

const props = withDefaults(defineProps<Props>(), {
  links: () => []
})

const api = useApi()
const toast = useToast()

const schema = z.object({
  name: z.string()
    .min(2, { error: 'El nombre necesita al menos 2 caracteres.' })
    .max(120, { error: 'El nombre no puede pasar de 120 caracteres.' }),
  email: z.email({ error: 'Ese correo no tiene forma de correo.' })
    .max(320, { error: 'El correo no puede pasar de 320 caracteres.' }),
  subject: z.string()
    .min(1, { error: 'Escribe un asunto.' })
    .max(160, { error: 'El asunto no puede pasar de 160 caracteres.' }),
  body: z.string()
    .min(10, { error: 'El mensaje necesita al menos 10 caracteres.' })
    .max(5000, { error: 'El mensaje no puede pasar de 5000 caracteres.' }),
  website: z.string().max(200).optional()
})

type State = z.infer<typeof schema>

const state = reactive<State>({
  name: '',
  email: '',
  subject: '',
  body: '',
  website: ''
})

const form = useTemplateRef('form')
const isSending = ref(false)

async function onSubmit({ data }: { data: State }) {
  isSending.value = true

  try {
    await api.post('/contact', data)

    toast.add({
      title: 'Mensaje enviado',
      description: 'Gracias por escribir; te contestaré lo antes posible.',
      icon: 'i-lucide-circle-check',
      color: 'success'
    })

    Object.assign(state, {
      name: '',
      email: '',
      subject: '',
      body: '',
      website: ''
    })

    form.value?.clear()
  } catch (error) {
    const problem = error instanceof ApiError ? error.problem : null

    if (problem?.status === 400 && problem.errors?.length) {
      form.value?.setErrors(
        problem.errors
          .filter(entry => entry.field)
          .map(entry => ({
            name: entry.field as string,
            message: entry.message
          }))
      )

      toast.add({
        title: 'Revisa el formulario',
        description: 'La API ha rechazado algún campo.',
        icon: 'i-lucide-triangle-alert',
        color: 'warning'
      })
    } else if (problem?.status === 429) {
      toast.add({
        title: 'Demasiados mensajes',
        description: problem.retryAfter
          ? `Vuelve a intentarlo en ${problem.retryAfter} segundos.`
          : 'Vuelve a intentarlo en unos segundos.',
        icon: 'i-lucide-clock',
        color: 'warning'
      })
    } else {
      toast.add({
        title: 'No se pudo enviar',
        description: problem?.detail ?? 'Inténtalo de nuevo en unos minutos.',
        icon: 'i-lucide-circle-x',
        color: 'error'
      })
    }
  } finally {
    isSending.value = false
  }
}

function onError({ errors }: {
  errors: { name?: string, message: string }[]
}) {
  toast.add({
    title: 'Revisa el formulario',
    description: `${errors.length} ${errors.length === 1 ? 'campo necesita' : 'campos necesitan'} atención.`,
    icon: 'i-lucide-triangle-alert',
    color: 'warning'
  })
}
</script>

<template>
  <UPageSection
    id="contacto"
    headline="HABLEMOS"
    title="¿Tienes un proyecto en mente?"
    description="Cuéntame tu idea, una propuesta de colaboración o cualquier duda. Estoy al tanto de los mensajes y te responderé lo antes posible."
    class="scroll-mt-24"
  >
    <div class="grid items-start gap-8 lg:grid-cols-[0.8fr_1.2fr] lg:gap-12">
      <!-- Presentación y enlaces configurables -->
      <div class="flex flex-col gap-6">
        <UCard>
          <div class="flex items-start gap-4">
            <div
              class="flex size-12 shrink-0 items-center justify-center
                     rounded-xl bg-primary/10 text-primary"
            >
              <UIcon
                name="i-lucide-message-circle"
                class="size-6"
              />
            </div>

            <div class="space-y-2">
              <h3 class="font-semibold">
                Hablemos de tu idea
              </h3>

              <p class="text-sm leading-6 text-muted">
                Si buscas desarrollar una aplicación, necesitas ayuda
                con una API o quieres colaborar en un proyecto,
                puedes escribirme mediante este formulario.
              </p>
            </div>
          </div>
        </UCard>

        <div
          v-if="props.links.length"
          class="space-y-4"
        >
          <h3 class="text-sm font-semibold text-muted">
            También puedes encontrarme en
          </h3>

          <UButton
            v-for="link in props.links"
            :key="link.to"
            :to="link.to"
            :target="link.to.startsWith('http') ? '_blank' : undefined"
            :rel="link.to.startsWith('http') ? 'noopener noreferrer' : undefined"
            :label="link.label"
            :icon="link.icon"
            trailing-icon="i-lucide-arrow-up-right"
            color="neutral"
            variant="outline"
            block
            class="justify-start"
          />
        </div>
      </div>

      <!-- Formulario: se conserva su integración con POST /contact -->
      <UCard>
        <template #header>
          <div class="flex items-center gap-3">
            <div
              class="flex size-10 shrink-0 items-center justify-center
                     rounded-lg bg-primary/10 text-primary"
            >
              <UIcon
                name="i-lucide-mail"
                class="size-5"
              />
            </div>

            <div>
              <h3 class="font-semibold">
                Envíame un mensaje
              </h3>

              <p class="text-sm text-muted">
                Los campos marcados son obligatorios.
              </p>
            </div>
          </div>
        </template>

        <UForm
          ref="form"
          :schema="schema"
          :state="state"
          class="flex flex-col gap-5"
          @submit="onSubmit"
          @error="onError"
        >
          <div class="grid gap-5 sm:grid-cols-2">
            <UFormField
              label="Nombre"
              name="name"
              required
            >
              <UInput
                v-model="state.name"
                placeholder="Tu nombre"
                icon="i-lucide-user"
                autocomplete="name"
                class="w-full"
              />
            </UFormField>

            <UFormField
              label="Correo electrónico"
              name="email"
              required
            >
              <UInput
                v-model="state.email"
                type="email"
                inputmode="email"
                placeholder="tu@correo.com"
                icon="i-lucide-mail"
                autocomplete="email"
                class="w-full"
              />
            </UFormField>
          </div>

          <UFormField
            label="Asunto"
            name="subject"
            required
          >
            <UInput
              v-model="state.subject"
              placeholder="¿Sobre qué quieres hablar?"
              icon="i-lucide-message-square"
              class="w-full"
            />
          </UFormField>

          <UFormField
            label="Mensaje"
            name="body"
            required
            description="Entre 10 y 5000 caracteres."
          >
            <UTextarea
              v-model="state.body"
              :rows="6"
              placeholder="Cuéntame los detalles de tu propuesta..."
              class="w-full"
            />
          </UFormField>

          <!-- Honeypot anti-spam -->
          <UFormField
            name="website"
            class="hidden"
          >
            <UInput
              v-model="state.website"
              tabindex="-1"
              autocomplete="off"
            />
          </UFormField>

          <div class="border-t border-default pt-5 flex flex-col justify-center items-center">
            <UButton
              type="submit"
              label="Enviar mensaje"
              icon="i-lucide-send"
              :loading="isSending"
              size="lg"
            />

            <p class="mt-3 sm:mt-6 text-center text-xs text-muted">
              Tus datos se utilizarán para responder a tu mensaje.
            </p>
          </div>
        </UForm>
      </UCard>
    </div>
  </UPageSection>
</template>
