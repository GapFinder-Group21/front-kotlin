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
├── data/         repositorios REST y GeoProbe (GPS / permisos)
├── ui/theme/     Palette y Typo
├── ui/kit/       componentes reutilizables (PillButton, FormField, DockBar, ...)
├── ui/nav/       Destination, StackNavigator, AppStage, Toaster
└── screens/      onboarding, home (pestañas), pairing (match), hangouts (open tables)
```

La navegación usa una pila propia (`StackNavigator`). Las pantallas de abajo en la pila siguen vivas, así que conservan su estado y sus timers mientras estás en otra pantalla.

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
