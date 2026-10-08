// ======================================================================
// DTO PUBLICOS DE LA API
//
// Espejo de api/src/main/java/dev/pepe1603/api/dto/publicapi: la fuente de
// verdad del contrato es docs/API.md, y si este archivo y la API divergen,
// manda la API. Los campos opcionales del backend (CV sin subir, enlaces
// sin configurar) llegan como null, no como ausencia: el tipo lo refleja
// para que la UI tenga que comprobarlo antes de pintar un enlace roto.
// ======================================================================

export interface Skill {
  name: string
  category: string
  level: string | null
}

export interface Experience {
  title: string
  company: string
  period: string
  type: string
  description: string
}

export interface ProfilePublicDTO {
  fullName: string
  headline: string
  bio: string
  location: string
  githubUrl: string | null
  linkedinUrl: string | null
  emailPublic: string | null
  websiteUrl: string | null
  cvUrlEs: string | null
  cvUrlEn: string | null
  avatarUrl: string | null
  skills: Skill[]
  experiences: Experience[]
}

/** Body de POST /contact. `website` es el honeypot: debe llegar vacío. */
export interface ContactRequest {
  name: string
  email: string
  subject: string
  body: string
  website?: string
}
