# Flujo de trabajo

## Modelo de ramas

| Rama | ¿En remoto? | Quién escribe | Qué contiene |
| --- | --- | --- | --- |
| `main` | sí | nadie directamente | solo releases |
| `develop` | sí | integración | el estado de lo terminado |
| `feat/*` `fix/*` `chore/*` | **no** | tú | trabajo en curso |

`develop` es el punto de integración: todo lo que está terminado llega ahí.
`main` es el estado publicable y no recibe trabajo directo; solo se mueve al
publicar una release. Esa separación es lo que permite que `main` sea siempre
desplegable sin revisar rama por rama.

Las ramas de trabajo **no se pushean**. Se fusionan en local contra `develop`
y lo que se sube es `develop`. No hay revisión de código en el proceso, así que
si en algún momento quieres revisar antes de integrar, la alternativa es pushear
la rama de trabajo y abrir un pull request hacia `develop` (ver más abajo).

## Empezar una feature

```bash
git switch develop
git pull origin develop
git switch -c feat/mi-cambio
```

Trabaja y commitea con normalidad. El CI corre en cada push, pero como la rama
no se pushea, conviene correrlo en local antes de integrar:

```bash
pnpm lint
pnpm typecheck
```

## Integrar en develop

```bash
git switch develop
git merge --no-ff feat/mi-cambio
git push origin develop
git branch -d feat/mi-cambio
```

`--no-ff` deja el merge como commit propio, que es lo que quieres en un
historial que va a releases: deja ver qué grupos de cambios entraron juntos.

Si `develop` está actualizado y hay varios commits en la feature, `git rebase`
sobre `develop` antes de integrar deja el historial lineal y más legible. Es
opcional, y como la rama es local no hay que coordinarlo con nadie.

## Publicar una release

```bash
git switch main
git merge --no-ff develop
git push origin main
git tag -a v1.0.0 -m "v1.0.0"
git push origin v1.0.0
```

## Con revisión de código

Si se pasa a un equipo donde el merge a `develop` debe revisarse:

```bash
git push -u origin feat/mi-cambio
```

y abrir un pull request hacia `develop`. El resto del flujo no cambia: `main`
sigue recibiendo únicamente releases.

## Autenticación

SSH. La llave vive en `~/.ssh/`, o sea es de la cuenta de usuario en esta
máquina, **no del repositorio**: una vez registrada en GitHub vale para todos
los repositorios de esa cuenta, incluidos los que se creen después. En otra
máquina hay que registrar su propia llave.

```bash
# La llave, una sola vez (no es del repo)
cat ~/.ssh/id_ed25519_github.pub
```

Se pega en GitHub → Settings → SSH and GPG keys → New SSH key. Comprobar:

```bash
ssh -T git@github.com
# Hi <usuario>! You've successfully authenticated...
```

Y el remoto en SSH, que es lo que hace que la llave se aplique sin configurar
nada por repositorio:

```bash
git remote set-url origin git@github.com:<cuenta>/<repo>.git
```

### Por qué SSH y no un token

Un token se guarda en el remoto de git o en un credential helper, hay que
recordarlo, hay que rotarlo y expira. La llave se registra una vez y no
caduca. Los tokens clásicos están además deprecados por GitHub; si alguna vez
hace falta HTTPS, el equivalente actual es un *fine-grained* token con alcance
`public_repo` para repositorios públicos.