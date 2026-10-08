# Almacenamiento — pepedev-portfolio

Por qué los ficheros del portafolio viven en un bucket S3 detrás de MinIO y no en un directorio
del disco, y qué decisiones **no** volver a abrir sin motivo nuevo. Si llegas aquí para cambiar
algo, léete primero la sección 4: la mitad de este documento son opciones descartadas a
propósito, y volver a proponerlas sin un problema nuevo es dar vueltas en círculo.

Las decisiones del correo están en [CORREO.md](CORREO.md), que es el mismo tipo de documento
para el otro subsistema cerrado del proyecto.

## 1. Qué hay

Un solo bucket, `portfolio`, y una sola operación de escritura: `POST /admin/storage`. Todo lo
demás son lecturas públicas o borrados de huérfanos.

| Flujo | Lo dispara | Qué ocurre en el bucket |
|---|---|---|
| Subida | `POST /admin/storage?use=…` | `putObject` con clave `<uuid>.<ext>` |
| Lectura | `GET /files/<uuid>.<ext>` | `getObject` y se devuelve al navegador |
| Descarga del CV | `GET /public/cv/{lang}` | nada: redirige (302) a la URL del CV |
| Huérfanos | al borrar o sustituir perfil, proyecto o certificado | `removeObject` si nadie lo referencia |

Variables, todas en `.env.example`:

| Variable | Para qué | Default |
|---|---|---|
| `APP_S3_ENDPOINT` | dónde está el MinIO | `http://localhost:9000` |
| `APP_S3_ACCESS_KEY` / `APP_S3_SECRET_KEY` | credenciales | **sin default**, obligatorias |
| `APP_S3_BUCKET` | nombre del bucket | `portfolio` |
| `APP_S3_REGION` | región de la firma S3 | `us-east-1` |
| `APP_STORAGE_PUBLIC_URL` | base de las URLs que se guardan en la BD | `http://localhost:8080/files` |

`APP_STORAGE_DIR` ha desaparecido: no hay disco. `APP_STORAGE_PUBLIC_URL` se queda con su
nombre a propósito, porque es contrato público (ver §2).

## 2. La invariante: la URL pública no cambia de forma

`avatarUrl`, `cvUrlEs`, `cvUrlEn`, `thumbnailUrl`, `gallery[].url` e `imageUrl` ya están
guardados en la base de datos, y `GET /public/cv/{lang}` devuelve un **302** a la que haya
guardada. Es decir: las URLs del almacenamiento son datos, no una consecuencia.

Por eso la URL pública sigue siendo `{APP_STORAGE_PUBLIC_URL}/<uuid>.<ext>`, exactamente igual
que cuando servía el resource handler de Spring. Solo cambia quién hay detrás:

```
antes:  navegador → GET /files/x.png → Spring → disco
ahora: navegador → GET /files/x.png → API → MinIO → navegador
```

El cambio de storage, en la práctica, **no obliga a migrar ninguna fila**. Ese era el punto
del contrato y por eso `StorageService.baseName(String)` — del que dependen
`StorageReferenceChecker` y `OrphanFileCleaner` — no ha necesitado tocar una línea: el último
segmento de la URL sigue siendo la clave del objeto.

Lo que sí hay que tener en cuenta: cambiar `APP_STORAGE_PUBLIC_URL` **sí** es una migración de
datos, porque todas las filas guardadas dejan de coincidir. Por eso la variable conserva el
nombre aunque su significado haya cambiado, para que nadie la renombre creyendo que es un
detalle.

## 3. Por qué la API hace de proxy y no manda al navegador a MinIO

Era la decisión con más riesgo de la migración, y está descartada en la §4. Lo que se paga por
elegir el proxy:

- **La API mueve los bytes.** Es el precio real. Aquí son imágenes de portafolio de unos cientos
  de KB y el tráfico es bajo; si algún día/appareciese un vídeo o un portfolio con visitas de
  verdad, la respuesta es poner un CDN delante, no cambiar el contrato.
- **Un salto más en cada descarga.** Un `<img src>` son dos peticiones en vez de una.

Y lo que se gana:

- **El bucket sigue siendo privado.** No hay política de lectura anónima, así que la URL no
  sirve solo con conocerla: hoy el nombre es un UUID, pero quien pueda escribir en el bucket
  podría poner un `logo.png` y se serviría igual. Con el bucket público eso pasa; aquí no.
- **El navegador no necesita alcanzar MinIO nunca.** Esto es lo que decide el paso a Docker:
  dentro del compose, MinIO es `http://minio:9000` y no está en Internet. Con URLs directas
  habría que publicar MinIO y darle un dominio; con el proxy, la API es la única que sale.
- **Se puede añadir autorización más adelante** sin cambiar nada de lo que hay delante: cuando
  haga falta proteger un fichero, se comprueba en el proxy y no hay URLs que retirar de la base
  de datos.

El proxy va en streaming (`StreamingResponseBody`) con la respuesta de MinIO como origen, así que
no se bufferea el fichero en memoria, y el content type se deduce de la extensión —que sale de
los magic bytes, no del nombre que manda el cliente— para que lo que se anuncia y lo que se
comprueba no puedan separarse.

## 4. Lo que no hay, y por qué

Estas son decisiones, no despistes pendientes.

| No hay | Por qué |
|---|---|
| **Bucket con lectura anónima** | Haría innecesaria la URL estable de la §2 y que cualquier URL servida sea pública para siempre. Con el proxy, leer ya exige pasar por la API. |
| **URL presignada de MinIO** | Caduca, y como mucho a 7 días. Aquí las URLs se guardan en la base de datos para siempre: una presignada en el momento de subir el CV haría que el CV dejara de abrirse pasado ese plazo. Descartado por la caducidad, no por la complejidad. |
| **302 a una presignada corta en cada visita** | Arregla el anterior sin caducidad en la base de datos, pero obliga al navegador a alcanzar MinIO —imposible dentro de Docker— y gasta una petición extra en cada imagen. Se descarta con el §3. |
| **SDK de AWS v2 en lugar del de MinIO** | Son ~30 dependencias (netty, httpclient5, eventstream, tres clientes HTTP) para hablar con el mismo protocolo. Si algún día el destino fuese AWS, el protocolo S3 ya está: el cambio cabe en `S3Properties` y `MinioConfig`. |
| **`io.minio:minio` 9.x** | A partir de la 8.6 arrastra okhttp 5, que es Kotlin Multiplatform, y Maven no resuelve solo la variante `okhttp-jvm`: habría que añadirla a mano. Se usa la 8.5.17, con API idéntica para lo que se usa aquí. Subir de versión es un commit de una línea más ese `okhttp-jvm`. |
| **Barrido periódico de huérfanos** | Hoy los huérfanos se limpian al borrar o sustituir, que es donde se sabe que algo dejó de usarse. Listar el bucket entero para cazar huérfanos tiene sentido cuando hay muchas subidas que se quedan a medias; para un admin solo, no. Cuando se añada, listar es más barato que recorrer un sistema de ficheros. |
| **Versionado del bucket** | Sobra: cada nombre es un UUID distinto, así que una versión antigua nunca se sirve. El borrado es `removeObject` y no hay versiones que restaurar. |
| **Streaming multipart / resumed uploads** | Los ficheros se validan por magic bytes y se sube de una vez, con un límite de 5/10 MB. El troceado que hace el SDK por debajo ya está. |
| **CDN delante del proxy** | No hay tráfico que lo justifique. Es lo primero que se pone si algún día lo hay, y no obliga a cambiar nada de lo de aquí. |

## 5. Migración de `uploads/`

Los once ficheros que había en `uploads/` están ya en el bucket. La herramienta que lo hizo
sigue en el repo por si hay que repetirla (un bucket nuevo, otro entorno, un fichero que se
había quedado fuera):

```bash
cd portfolio-api
./mvnw -o test -Dtest=UploadsToBucketMigrationIT
```

Es idempotente (si el objeto ya está, lo dice y lo deja), **relee del bucket cada objeto que
sube** para comprobar que trae los mismos bytes, y **no borra nada**.

### Por qué `uploads/` sigue ahí

Porque copiar se deshace y borrar el directorio no, y hasta que no se ha comprobado que la web
sirve las mismas imágenes desde el bucket, borrarlo es tires lo único que queda. El orden para
retirarlo, cuando toca:

1. La herramienta ha subido todo y ha releído todo.
2. `GET /files/<nombre>` devuelve los bytes de cada fichero de `uploads/`.
3. Se revisan en la web las páginas con imágenes: portada, un proyecto con galería, el CV.
4. Solo entonces, `uploads/` se borra o se archiva.

## 6. Cómo se comprueban estas decisiones

255 tests en total. `./mvnw -o clean test` desde `portfolio-api/`, sin MinIO levantado: el SDK va
mockeado. El único que toca el servidor es el de la migración, y no lo recoge la suite.

| Decisión | Test |
|---|---|
| El proxy no busca en el bucket un nombre que no es `UUID.ext` | `FileServingControllerTest.unNombreQueNoEsDelStorageNiSiquieraSeBuscaEnElBucket` |
| Objeto que no está es 404, MinIO caído es 500 | `FileServingControllerTest.objetoQueNoEstaEnElBucketEs404YNo500`, `…siElBucketNoRespondeEs500YNoUn404Enganoso` |
| El 304 no vuelve a bajar el objeto | `FileServingControllerTest.conIfNoneMatchIgualDevuelve304SinVolverABuscarElObjeto` |
| El content type sale de la extensión | `FileServingControllerTest.elContentTypeSeSacaDeLaExtensionNoDeLoQueDigaElCliente` |
| El bucket se crea al arrancar si falta, con su región | `StorageServiceBootstrapTest.creaElBucketConSuRegionSiNoExiste` |
| Si MinIO no responde, la app no arranca | `StorageServiceBootstrapTest.siMinioNoRespondeFallaAlArrancarDiciendoQueMinio` |
| Se sube al bucket correcto, con el content type y los bytes de verdad | `StorageServiceTest.pngAvatarSeGuardaConUrlPublicaYNombreUUID` |
| Solo se toca lo que es clave de objeto | `StorageServiceTest.isManagedNameEsLaFronteraDelBucket`, `StorageServiceTest.deleteByUrlNoMandaAlBucketUnaClaveQueNoEsDelStorage` |
| La migración sube, relee y es idempotente | `UploadsToBucketMigrationIT` (opt-in, no lo corre la suite) |

`StorageReferenceChecker` y `OrphanFileCleaner` siguen igual y sus tests no han cambiado: la
comprobación de referencias es contra la base de datos y el borrado es una llamada a
`deleteByName`, que ya no tiene nada de disco dentro. Lo que hace S3 es más simple, no más
complicado.

## 7. Referencia rápida

```
S3Properties (endpoint, claves, bucket, región) + MinioConfig → MinioClient
StorageService
  ├── store(use, fichero)  → valida magic bytes y tamaño, putObject, devuelve URL
  ├── deleteByUrl / deleteByName → removeObject, best-effort
  ├── baseName(url) / isManagedName(name) → qué es una clave de objeto
  └── ensureBucket() → @PostConstruct: crea el bucket si falta
FileServingController  → GET /files/{name}, proxy en streaming, 304 con If-None-Match
OrphanFileCleaner      → borra lo que ya no referencia nadie
StorageReferenceChecker → mira perfil, proyectos y certificados
```

## 8. Qué queda para el paso a Docker y a Nuxt

Nada de esto está hecho, y nada de ello bloquea lo que sí está:

- **MinIO en Docker.** Solo `APP_S3_ENDPOINT=http://minio:9000` y las credenciales del
  compose. El bucket lo crea la API, así que no hay script de arranque que mantener.
- **Que la API quede accesible desde el contenedor de MinIO y al revés** (la red del compose),
  y que `APP_STORAGE_PUBLIC_URL` pase a ser el dominio público detrás del proxy TLS.
- **Que Nuxt consuma `/files/**`** sin cambios: es la misma URL de siempre, así que el front
  no necesita enterarse de nada.
- **Copiar el bucket al desplegar en Hostinger**, donde los ficheros de producción dejarán de
  estar en el `tarball` que hoy se manda por `rsync`. Es el punto donde sí hace falta decidir
  entre volúmenes y réplicas.