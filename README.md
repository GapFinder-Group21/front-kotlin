# GAP FINDER — Android (Kotlin + Jetpack Compose)

App Android nativa de GAP FINDER. Tiene la misma interfaz y los mismos flujos que la versión Flutter (`front`): mismos colores, tipografías (Montserrat, Magra y Cambay), íconos y pantallas, y consume el mismo backend.

## Requisitos

- Android Studio (Ladybug o más reciente) con JDK 17
- Android SDK 36
- Dispositivo o emulador con Android 8.0 (API 26) o superior

## Cómo correrla

1. Abre la carpeta `front-kotlin` en Android Studio y deja que sincronice Gradle.
2. Cambia la IP del backend en `app/src/main/java/co/gapfinder/mobile/foundation/ServerEnv.kt`.
3. Ejecuta la configuración `app`.

## Estructura

```
co.gapfinder.mobile
├── foundation/   red (HttpGateway), sesión cifrada (SessionVault), JSON y fechas
├── domain/       modelos (Member, FreeWindow, Pairing, Hangout, ...) y enums
├── data/         repositorios REST, GeoProbe (GPS / permisos), local/ (Room) y outbox/ (cola sin red)
├── ui/theme/     Palette y Typo
├── ui/kit/       componentes reutilizables (PillButton, FormField, DockBar, ...)
├── ui/nav/       Destination, StackNavigator, AppStage, Toaster
└── screens/      onboarding, home (pestañas), pairing (match), hangouts (open tables)
```

La navegación usa una pila propia (`StackNavigator`). Las pantallas de abajo en la pila siguen vivas, así que conservan su estado y sus timers mientras estás en otra pantalla.

## Datos sin red

- **Room** (`data/local/`): horario, mis open tables y el borrador del wizard. Las pantallas leen primero de Room y después actualizan desde la red. Se accede por `LocalStore`.
- **Cola de acciones** (`data/outbox/`): lo que se hace sin red se guarda en la tabla `pending_actions` y un worker de WorkManager lo manda cuando vuelve la conexión.

Para encolar una acción propia:

```kotlin
// Una petición HTTP con el token del usuario
Outbox.enqueueRequest("POST", "/ruta", jsonOf("campo" to valor))

// Un tipo con lógica propia: se registra una vez al iniciar la app...
Outbox.register("mi.tipo") { payload -> HttpGateway.send("/ruta", payload).toDelivery() }
// ...y se encola donde haga falta
Outbox.enqueue("mi.tipo", payload)
```

Si el backend responde 2xx la acción se borra. Si responde 401/403/408/429/5xx o no hay red, se reintenta con backoff. Cualquier otro 4xx la descarta.

## Pantallas

| Pestaña | Qué hace |
| --- | --- |
| Schedule | Clases de hoy y los GAPs que el backend calcula para la semana. |
| Friends | Buscar usuarios por nombre, enviar y responder solicitudes de amistad, y ver qué amigos tienen GAP ahora. |
| Match | Buscar a alguien libre al mismo tiempo, priorizando por carrera, intereses o esfuerzo. Al aceptarse el match se muestra el teléfono de la otra persona. |
| Open Tables | Mesas abiertas con cupo máximo; crear, unirse y ver las propias. Al entrar a un edificio donde hay amigos aparece un aviso. |
| Suggest | Edificio que más frecuentas y las mesas abiertas que hay ahí. |

## Nombres frente al backend

Los modelos tienen nombre propio en la app; las claves JSON son las del backend.

| App | Backend |
| --- | --- |
| `Member` | User |
| `Hobby` | Interest |
| `Pastime` | Activity |
| `CampusSpot` | Building |
| `Lecture` | ClassBlock |
| `FreeWindow` | Gap |
| `Bond` | Friendship |
| `Pairing` / `PairingProspect` | Match / MatchCandidate |
| `Hangout` / `HangoutAttendee` | OpenTable / OpenTableParticipant |
| `Alert` | Notification |
| `EnergyLevel` | EffortTypeEnum |
