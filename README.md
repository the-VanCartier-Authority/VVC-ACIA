# VVC-ACIA

Agente Android local de The Van Cartier Authority. La aplicación ejecuta inferencia LiteRT offline con modelos empaquetados y dispone de una capa modular para instalar versiones de modelos sin recompilar el APK.

## Arquitectura modular

El paquete `app/src/main/java/com/vancartier/vvcmobileagentcore/modelruntime/` contiene:

- `ModelContracts.kt`: descriptores, manifiestos, eventos de progreso y contrato `LlmProvider`.
- `ModelStore.kt`: almacenamiento privado versionado, punteros activos y activación atómica mediante archivo temporal y rename.
- `ModelRuntime.kt`: coordinación de registro, validación, instalación, activación y rollback manual.
- `ModelRegistries.kt`: catálogo local JSON, catálogo HTTPS y catálogo firmado con Ed25519.
- `ModelDownloader.kt`: descarga HTTPS a `.partial`, validación de tamaño y SHA-256 antes de instalar.
- `ModelUpdateWorker.kt`: sincronización persistente con WorkManager y reintentos.

El almacenamiento de modelos descargados es privado a la aplicación:

```text
files/models/
├── versions/<model-id>/<version>.artifact
├── active/<model-id>                 # versión activa
├── temp/<model-id>-<version>.partial
└── catalog.json
```

Flujo seguro:

```text
Catálogo → HTTPS → .partial → tamaño/SHA-256 → instalación → puntero atómico → lazy loading
```

`VvcEdgeModelManager` valida los assets mediante sus archivos `.sha256`, y prioriza un artefacto descargado activo que coincida con la capacidad solicitada. Si no existe uno válido, utiliza los modelos de `app/src/main/assets/models/` como fallback offline.

## Registro remoto opcional

No se configura ningún endpoint remoto por defecto. Para sincronizar de forma explícita:

```kotlin
ModelRuntime(context).synchronize(HttpModelRegistry("https://example.invalid/models.json"))
```

El endpoint debe ser HTTPS. Para actualizaciones periódicas se puede usar `ModelUpdateScheduler.schedule(context, "https://example.invalid/models.json")`. `SignedHttpModelRegistry` permite validar un sobre `{ "payload": "...", "signature": "..." }` con una clave pública Ed25519 antes de aceptar el manifiesto. Las versiones instaladas se conservan; `ModelRuntime.rollback(id, descriptor)` puede reactivar una versión anterior ya instalada.

## Proveedores LLM

`LlmProvider` es una abstracción para que un proveedor local o remoto pueda incorporarse sin acoplar el resto de la app a una implementación concreta. `UnsupportedLocalLlmProvider` falla explícitamente.

**LiteRT-LM no está implementado en esta fase.** El proyecto usa LiteRT Interpreter para los clasificadores de audio, imagen y texto existentes, pero todavía no incluye runtime LiteRT-LM, tokenizer, conversación ni streaming LLM. No se declaran resultados simulados ni un proveedor remoto inexistente.

## Modelos actuales y seguridad

Los assets existentes viven en `app/src/main/assets/models/`. Cada modelo debe tener su sidecar `.sha256`. El script de descarga es `python3 tools/download_edge_models.py`. La app no carga un modelo empaquetado si falta el sidecar o el hash no coincide. Las anomalías de integridad y las salidas de baja confianza generan notificaciones locales.

## Compilación y validación

Requisitos: Java 17, Android SDK con API 35 y Gradle Wrapper 8.13.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleDebug
./gradlew :app:test
```

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. El workflow `.github/workflows/android-build.yml` compila `assembleDebug` en CI. La validación de descarga, firma y rollback requiere un catálogo y artefactos de prueba; no se activan por defecto en producción.

## Fase MODEL RUNTIME MODULARITY — 1

Esta fase implementa modularidad de modelos sin recompilar, fallback offline, descarga persistente, validación criptográfica, activación atómica, conservación de versiones y rollback manual. Quedan fuera de alcance la implementación real de LiteRT-LM, un backend de modelos, credenciales de producción y rollback automático tras crash.
