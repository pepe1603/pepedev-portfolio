// ======================================================================
// TIPOS ADMIN DE LA API
//
// Espejo de api/src/main/java/dev/pepe1603/api/entity (Project y
// AdminProjectController) y de docs/API.md. Si este archivo y la API
// divergen, manda la API.
//
// --------------------------------------------------------------------------
// LA DIFERENCIA CON LOS DTO PÚBLICOS (utils/types.ts)
//
// /public/** devuelve el texto YA RESUELTO al idioma pedido: una sola
// cadena. /admin/** devuelve la ENTIDAD tal cual la guarda la base de
// datos, con los campos bilingües sin resolver (`{"es": "...", "en": "..."}`)
// en columnas JSONB. De ahí LocalizedText, que no existe en el lado
// público.
//
// Los campos nullable del backend llegan como `null`, no como ausencia, y
// el tipo lo refleja para que la UI tenga que comprobarlo antes de pintar.
// ======================================================================

/** Campo bilingüe tal y como lo guarda la base de datos (JSONB {es,en}). */
export interface LocalizedText {
  es?: string
  en?: string
}

/** Estado de un proyecto o certificado. `POST` crea siempre DRAFT. */
export type ProjectStatus = 'DRAFT' | 'PUBLISHED'

/**
 * Los certificados usan exactamente el mismo par de valores: en Java es el
 * enum `CertificateStatus`, pero su identidad no cambia (DRAFT | PUBLISHED).
 */
export type CertificateStatus = ProjectStatus

/**
 * Muestra un campo bilingüe tal y como viene de /admin/**: se prefiere el
 * español y, si no tiene, el inglés.
 *
 * Es el equivalente admin de lo que ya resuelve el servidor en el lado
 * público (que devuelve una sola cadena en el idioma pedido). Devuelve cadena
 * vacía si el campo está vacío o es null, para que quien pinte no tenga que
 * comprobar nada más.
 */
export function localizedText(text: LocalizedText | null | undefined): string {
  return text?.es?.trim() || text?.en?.trim() || ''
}

// ---------------------------------------------------------------------------
// FECHAS
//
// El backend devuelve DOS cosas distintas y el JSON las distingue bien:
//   · LocalDate → 'yyyy-MM-dd' SIN hora: issueDate, expiryDate, periodStart…
//   · Instant   → ISO-8601 completo: createdAt, publishedAt, updatedAt…
//
// OJO con los date-only: `new Date('yyyy-MM-dd')` se interpreta como
// MEDIANOCHE UTC, así que en cualquier zona al oeste de Greenwich (toda
// América) formatearlo en local salta al día ANTERIOR. Un certificado
// emitido el 2025-03-01 se vería como "28 feb 2025" en México. Para los
// date-only se parsea como hora local añadiendo T00:00:00, que no mueve el
// día en ninguna zona; los instantes se quedan tal cual.
// ---------------------------------------------------------------------------
const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/

export function adminDate(
  value: string | null | undefined,
  options: Intl.DateTimeFormatOptions = { day: '2-digit', month: 'short', year: 'numeric' }
): string {
  if (!value) {
    return '—'
  }
  const date = DATE_ONLY.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return new Intl.DateTimeFormat('es-ES', options).format(date)
}

/** Registro de la galería. Mismo shape que GalleryImage del lado público. */
export interface AdminGalleryImage {
  url: string
  alt: string | null
  caption: string | null
}

/**
 * Entidad que devuelven GET /admin/projects y GET /admin/projects/{id}.
 * Trae más que el DTO público: id, status, fechas de publicación y
 * auditoría, y los textos bilingües sin resolver.
 */
export interface AdminProject {
  id: string
  slug: string
  title: LocalizedText
  subtitle: LocalizedText | null
  summary: LocalizedText | null
  descriptionMd: LocalizedText | null
  thumbnailUrl: string | null
  gallery: AdminGalleryImage[]
  repoUrl: string | null
  demoUrl: string | null
  stack: string[]
  /** LocalDate del backend: 'yyyy-MM-dd' en JSON. */
  periodStart: string | null
  periodEnd: string | null
  featured: boolean
  sortOrder: number
  status: ProjectStatus
  /** Instant del backend: ISO-8601. null si sigue en DRAFT. */
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * Body de POST y PUT /admin/projects (ProjectRequest en la API).
 *
 * OJO con `featured`: es un boolean PRIMITIVO en Java y Spring Boot 4.x
 * mantiene FAIL_ON_NULL_FOR_PRIMITIVES, así que omitirlo no es un error de
 * validación sino de parseo: 400 "Failed to read request". Enviarlo
 * SIEMPRE, aunque sea false.
 *
 * `PUT` es edición completa: lo que no venga se guarda como null (salvo
 * sortOrder→0 y gallery/stack→[]), así que hay que mandar todo.
 */
export interface ProjectRequest {
  slug: string
  title: LocalizedText
  subtitle?: LocalizedText | null
  summary?: LocalizedText | null
  descriptionMd?: LocalizedText | null
  thumbnailUrl?: string | null
  gallery?: AdminGalleryImage[]
  repoUrl?: string | null
  demoUrl?: string | null
  stack?: string[]
  /** LocalDate: 'yyyy-MM-dd'. */
  periodStart?: string | null
  periodEnd?: string | null
  /** Primitivo en la API: enviar siempre, aunque sea false. */
  featured: boolean
  sortOrder?: number
}

// ---------------------------------------------------------------------------
// CERTIFICADOS
//
// Mismo esquema que proyectos (lista completa sin paginar, POST/PUT, PATCH
// publish|unpublish, PUT order y DELETE), pero bastante más plano: título
// bilingüe + emisor + tipo, sin galería, stack ni descripción.
//
// Hay dos diferencias que importan al pintar:
//  · `title` lleva la MISMA restricción LocalizedNonBlank que en proyectos:
//    hay que tener es O en, no los dos.
//  · NO existe `updatedAt` en la entidad, solo `createdAt` y `publishedAt`.
// ---------------------------------------------------------------------------

/** Certificado o curso. El panel lo traduce a 'Certificado' y 'Curso'. */
export type CertificateKind = 'CERTIFICATE' | 'COURSE'

export interface AdminCertificate {
  id: string
  title: LocalizedText
  issuer: string
  kind: CertificateKind
  /** LocalDate: 'yyyy-MM-dd', igual que en proyectos. */
  issueDate: string | null
  /** null = sin caducidad. */
  expiryDate: string | null
  credentialUrl: string | null
  /** Imagen del certificado (use='image' de POST /admin/storage). */
  imageUrl: string | null
  featured: boolean
  sortOrder: number
  status: CertificateStatus
  publishedAt: string | null
  createdAt: string
}

/**
 * Body de POST y PUT /admin/certificates (CertificateRequest en la API).
 *
 * `featured` y `sortOrder` se comportan igual que en proyectos: el primero es
 * boolean primitivo y omitirlo da 400 de parseo, el segundo cae a 0 si no
 * viene. El resto de opcionales se envía null en lugar de omitirse, porque
 * `PUT` es edición completa y lo ausente se guarda vacío.
 */
export interface CertificateRequest {
  title: LocalizedText
  issuer: string
  kind: CertificateKind
  issueDate?: string | null
  expiryDate?: string | null
  credentialUrl?: string | null
  imageUrl?: string | null
  /** Primitivo en la API: enviar siempre, aunque sea false. */
  featured: boolean
  sortOrder?: number
}

/** Mismo criterio que la página pública de certificados. */
export function certificateKindLabel(kind: CertificateKind): string {
  return kind === 'COURSE' ? 'Curso' : 'Certificado'
}

/**
 * Usos permitidos de POST /admin/storage. `image` es el genérico (imagen de
 * certificado); `cv` es el único que admite PDF.
 */
export type StorageUse = 'avatar' | 'thumbnail' | 'gallery' | 'image' | 'cv'

/** Respuesta de POST /admin/storage: la URL pública ya montada por la API. */
export interface StorageUploadResult {
  url: string
}
