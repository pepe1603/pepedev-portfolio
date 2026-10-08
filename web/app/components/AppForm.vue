<script setup lang="ts">
// ======================================================================
// AppForm: UForm + Zod.
//
// Zod NO hace la validacion. UForm la hace, leyendo el schema. Lo que
// hace Zod es DESCRIBIR las reglas en un sitio, y de ese schema sale el
// tipo del estado. Si el nombre se acorta a "loquesea", UForm pinta el
// error debajo del campo sin que nadie escriba esa parte.
//
// --------------------------------------------------------------------------
// POR QUE ESTO NO ES "VALIDAR A MANO CON UN IF"
//
// Tres cosas que salen gratis con el schema y que a mano se olvidan:
//
//   1. El tipo del estado. `z.infer` saca el tipo del schema, asi que el
//      estado no puede quedar desincronizado con las reglas: si el schema
//      exige un campo, el estado lo tiene.
//
//   2. Los errores van a su campo. UForm convierte cada issue de Zod en un
//      error con `name` = el path del issue, y UFormField lo busca por su
//      prop name. Por eso hay que poner el MISMO nombre en el name del
//      campo, en el v-model del input y en la clave del schema. Si no
//      coinciden, el error aparece y nadie sabe de quién es.
//
//   3. Las reglas entre campos. `.refine()` sobre el objeto entero es lo
//      que mas ahorra: comparar dos campos a mano exige accesso a los dos,
//      y se acaba validando la mitad.
//
// --------------------------------------------------------------------------
// POR QUE `z.email()` Y NO `z.string().email()`
//
// En Zod 4 lo segundo sigue funcionando, pero esta marcado como obsoleto:
// el formato es ya un tipo de primer nivel y se escribe solo. Menos
// codigo y la intencion mas clara.
//
// --------------------------------------------------------------------------
// POR QUE LOS MENSAJES EN CASTELLANO
//
// Los mensajes por defecto de Zod estan en ingles ("Invalid email
// address"). Un formulario de una aplicacion en castellano que suelta
// "Invalid email address" debajo del campo parece roto. Cada regla lleva
// su mensaje; la sintaxis de Zod 4 es `{ error: '...' }`, no el segundo
// argumento de Zod 3.
//
// --------------------------------------------------------------------------
// POR QUE `terms` NO ES `z.literal(true)`
//
// Seria lo natural, pero `z.infer` daria `true` como tipo del campo, y
// entonces el estado no podria guardar `false`, que es justo lo que pasa
// mientras el usuario no ha marcado la casilla. El tipo dejaria de
// describir lo que el formulario puede estar conteniendo.
//
// La solucion es `z.boolean().refine(...)`: el tipo sigue siendo boolean
// y la regla se comprueba igual.

// zod es una peer dependency OPCIONAL de @nuxt/ui: esta en su node_modules
// pero no se puede importar desde aqui sin instalarla en el proyecto. Por
// eso figura como dependencia normal en package.json aunque no se use
// para nada mas.
import { z } from 'zod'

const toast = useToast()

const schema = z.object({
  name: z.string().min(2, { error: 'El nombre necesita al menos 2 caracteres.' }),
  email: z.email({ error: 'Ese correo no tiene forma de correo.' }),
  password: z.string().min(8, { error: 'La contraseña necesita al menos 8 caracteres.' }),
  // Va en la forma del objeto y no solo en el refine de abajo, o z.infer no
  // lo contaria y el estado no podria guardarlo.
  confirmPassword: z.string(),
  role: z.enum(['admin', 'editor', 'viewer'], {
    error: 'Elige un rol.'
  }),
  terms: z.boolean().refine(accepted => accepted, {
    error: 'Hay que aceptar los términos.'
  })
}).refine(data => data.password === data.confirmPassword, {
  error: 'Las contraseñas no coinciden.',
  // Sin este path, el error sale con name "" y UFormField no lo encuentra:
  // aparece en el formulario sin dueño, o no aparece. El path dice a qué
  // campo se le atribuye.
  path: ['confirmPassword']
})

// El tipo sale del schema, no se escribe a mano. Si el schema exige un
// campo, el estado lo tiene; y si sobra un campo en el estado, el tipo lo
// delata al escribir esto, que es justo cuando se puede arreglar.
type State = z.infer<typeof schema>

const state = reactive<State>({
  name: '',
  email: '',
  password: '',
  confirmPassword: '',
  role: 'viewer',
  terms: false
})

// UForm expone su API con un ref. Se necesita para limpiar el formulario
// tras enviar y para saber si el usuario ha tocado algo (dirty).
const form = useTemplateRef('form')

const isSaving = ref(false)

const roles = [
  { label: 'Administrador', value: 'admin' },
  { label: 'Editor', value: 'editor' },
  { label: 'Lector', value: 'viewer' }
]

async function onSubmit({ data }: { data: State }) {
  // `data` ya viene validado y con los tipos puestos por el schema. No
  // hace falta volver a comprobar nada aqui: si llega a este punto, el
  // formulario es valido.
  isSaving.value = true

  // Sustituir por la peticion real.
  await new Promise(resolve => setTimeout(resolve, 900))

  isSaving.value = false

  // notice() de utils/appNotify.ts, que es notify() de useToast() con el
  // objeto entero. Aqui se usa el composable porque el aviso lleva titulo
  // Y descripcion, y para eso el atajo se queda corto.
  toast.add({
    title: `Cuenta creada para ${data.name}`,
    description: `${data.email} con rol de ${roles.find(role => role.value === data.role)?.label.toLowerCase()}.`,
    icon: 'i-lucide-circle-check',
    color: 'success'
  })

  // clear() no es vaciar el estado: es limpiar los errores y el estado
  // interno de "tocado". Sin esto, el formulario seguiria marcando campos
  // como tocados despues de enviar, y el usuario veria errores de un
  // formulario que ya no esta delante.
  form.value?.clear()
}

// @error sale cuando la validacion falla, con la lista de errores ya
// calculada. Sirve para el aviso generico de "revisa el formulario".
const errorCount = ref(0)

function onError({ errors }: { errors: { name?: string, message: string }[] }) {
  errorCount.value = errors.length

  toast.add({
    title: 'Revisa el formulario',
    description: `${errors.length} ${errors.length === 1 ? 'campo necesita' : 'campos necesitan'} atención.`,
    icon: 'i-lucide-triangle-alert',
    color: 'warning'
  })
}
</script>

<template>
  <UForm
    ref="form"
    :schema="schema"
    :state="state"
    class="flex flex-col gap-6"
    @submit="onSubmit"
    @error="onError"
  >
    <UFormField
      label="Nombre"
      name="name"
      required
      description="Como aparecerá en la lista de miembros."
    >
      <UInput
        v-model="state.name"
        placeholder="Ada Lovelace"
        icon="i-lucide-user"
        autocomplete="name"
        class="w-full"
      />
    </UFormField>

    <UFormField
      label="Correo"
      name="email"
      required
      description="Solo para el aviso de contraseña perdida."
    >
      <UInput
        v-model="state.email"
        type="email"
        inputmode="email"
        placeholder="ada@correo.com"
        icon="i-lucide-mail"
        autocomplete="email"
        class="w-full"
      />
    </UFormField>

    <UFormField
      label="Contraseña"
      name="password"
      required
      description="Mínimo 8 caracteres."
    >
      <UInput
        v-model="state.password"
        type="password"
        placeholder="········"
        icon="i-lucide-lock"
        autocomplete="new-password"
        class="w-full"
      />
    </UFormField>

    <UFormField
      label="Repite la contraseña"
      name="confirmPassword"
      required
    >
      <UInput
        v-model="state.confirmPassword"
        type="password"
        placeholder="········"
        icon="i-lucide-lock"
        autocomplete="new-password"
        class="w-full"
      />
    </UFormField>

    <UFormField
      label="Rol"
      name="role"
      required
      description="Lo que puede hacer con el contenido."
    >
      <USelect
        v-model="state.role"
        :items="roles"
        class="w-full"
      />
    </UFormField>

    <UFormField name="terms">
      <USwitch
        v-model="state.terms"
        label="Acepto los términos y la política de privacidad"
        description="Sin esto no se puede enviar."
        :color="state.terms ? 'success' : 'neutral'"
      />
    </UFormField>

    <div class="flex items-center justify-between gap-4 border-t border-default pt-6">
      <p
        v-if="errorCount > 0"
        class="text-warning text-sm"
      >
        {{ errorCount }} {{ errorCount === 1 ? 'campo con errores' : 'campos con errores' }}
      </p>

      <span v-else />

      <div class="flex gap-2">
        <UButton
          label="Limpiar"
          color="neutral"
          variant="ghost"
          :disabled="isSaving"
          @click="form?.clear()"
        />

        <UButton
          type="submit"
          label="Enviar"
          icon="i-lucide-send"
          :loading="isSaving"
        />
      </div>
    </div>
  </UForm>
</template>
