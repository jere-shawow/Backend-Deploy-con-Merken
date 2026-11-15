# API para frontends (JavaFX, web en la nube, móvil)

Base: `http://<host>:8080`. Todas las respuestas son JSON.

## CORS (front web en otro dominio)

El navegador solo deja que un front web llame a la API si el backend lo permite.
Variable `CORS_ALLOWED_ORIGINS` (en `.env` del EC2), lista separada por comas:

```
CORS_ALLOWED_ORIGINS=https://mi-front.example.com,http://localhost:5173
```

Por defecto es `*` (cualquier origen). En producción pon solo la URL de tu front.

## Crear / editar un astronauta (con Garmin opcional)

`POST /api/astronauts` · `PUT /api/astronauts/{id}`

```json
{
  "astronautName": "Valentina Rojas",
  "spaceRadiation": 0.72,
  "distanceFromEarth": 408000,
  "bloodPressure": "118/76",
  "heartRate": 68,
  "bloodSugar": 105,
  "bodyTemperature": 36.7,
  "sleepHours": 7.5,
  "nutrition": 2600,
  "oxygenInBlood": 98,
  "bodyBattery": 72,
  "hrv": 48,
  "mood": "BUENO",
  "garminApiKey": "GCDC_..."
}
```

| Campo | Tipo | Obligatorio | Validación |
|---|---|---|---|
| `astronautName` | texto | sí | máx. 100 |
| `spaceRadiation` | número (mSv/día) | sí | ≥ 0 |
| `distanceFromEarth` | número (km) | sí | ≥ 0 |
| `bloodPressure` | texto `"sistólica/diastólica"` | sí | formato `120/80` |
| `heartRate` | entero (bpm) | sí | 20 – 250 |
| `bloodSugar` | número (mg/dL) | sí | ≥ 0 |
| `bodyTemperature` | número (°C) | sí | 30 – 45 |
| `sleepHours` | número (h) | sí | 0 – 24 |
| `nutrition` | entero (kcal/día) | sí | 0 – 10000 |
| `oxygenInBlood` | número (%) | sí | 0 – 100 |
| `bodyBattery` | entero | no | 0 – 100 |
| `hrv` | número (ms) | no | 0 – 300 |
| `mood` | texto | sí | `EXCELENTE`, `BUENO`, `NEUTRAL`, `CANSADO`, `ESTRESADO`, `ANSIOSO` (mayúsculas o minúsculas) |
| `garminApiKey` (o `apiKey`) | texto | no | API key de Garmin Connect Personal API |

- Respuesta `201` con el astronauta guardado (con su `id`) y la cabecera `Location`.
- `400` con `{"errors": {campo: mensaje}}` si un campo no es válido.
- **Garmin:** si viene `garminApiKey`, se vincula en la misma petición (se guarda en la tabla
  `garmin_links`, relacionada por `astronaut_id`). Si la key no sirve, el astronauta **igual queda
  guardado** y la respuesta trae `"garminLinkError": "..."` y `"garminLinked": false`.
  La key nunca se devuelve en las respuestas.
- También se puede vincular aparte: `PUT /api/astronauts/{id}/garmin` con `{"apiKey": "GCDC_..."}`.
- Con Garmin vinculado, `heartRate`, `sleepHours`, `bodyBattery` y `hrv` se actualizan solos desde el reloj.

## Rangos de salud

`GET /api/health-ranges` devuelve la definición completa (mismo archivo que usa el backend):
por parámetro, `field` (campo del astronauta), `unit`, `notify` y los rangos `very_low` … `very_high`
con `min`, `max`, `min_inclusive`, `max_inclusive`, `severity` (`ok` verde, `warning` naranjo,
`critical` rojo) y `description`.

| Parámetro | Campo | Normal | Notifica |
|---|---|---|---|
| `radiation` | `spaceRadiation` | 0.5 – 1.0 mSv/día (menos también es ok) | alto / muy alto |
| `distance` | `distanceFromEarth` | 2.000 – 384.400 km | no (informativo) |
| `blood_pressure_systolic` / `_diastolic` | `bloodPressure` | 100–120 / 60–80 mmHg | sí (vale el peor de los dos) |
| `pulse` | `heartRate` | 60 – 100 | sí |
| `glucose` | `bloodSugar` | 100 – 125 mg/dL | sí |
| `temperature` | `bodyTemperature` | 36.1 – 37.2 °C | sí |
| `sleep` | `sleepHours` | 7 – 9 h | sí |
| `oxygen` | `oxygenInBlood` | 95 – 100 % | bajo / muy bajo |
| `body_battery` | `bodyBattery` | 51 – 75 (alto = bueno) | bajo / muy bajo |
| `hrv` | `hrv` | 38 – 75 ms (alto = bueno) | bajo / muy bajo |

Ánimo (`mood`) y nutrición (`nutrition`, kcal) no tienen rangos.

## Estado de salud ya calculado

Para no repetir la lógica de rangos en cada front:

- `GET /api/astronauts/health-status` → todos los astronautas
- `GET /api/astronauts/{id}/health-status` → uno

```json
{
  "astronautId": 1,
  "astronautName": "Valentina Rojas",
  "overall": "WARNING",
  "parameters": [
    {"parameter": "pulse", "field": "heartRate", "name": "Frecuencia cardíaca", "value": "108",
     "unit": "lpm", "status": "HIGH", "severity": "WARNING", "description": "101 – 115 lpm", "notify": true},
    {"parameter": "blood_pressure", "field": "bloodPressure", "name": "Presión arterial", "value": "118/76",
     "unit": "mmHg", "status": "NORMAL", "severity": "OK", "description": "100 – 120 (Óptimo / Normal)", "notify": true},
    {"parameter": "hrv", "field": "hrv", "value": null, "status": null, "severity": null, "...": "sin dato"}
  ]
}
```

`severity`: `OK` (verde), `WARNING` (naranjo), `CRITICAL` (rojo). `overall` es la peor.

## Recomendación IA de una alerta (Amazon Nova Pro)

`GET /api/notifications/{id}/recommendation`

Se pide **al abrir** una notificación automática. El backend llama al análisis Nova Pro
(`docs/bedrock-analysis.md`) con los datos actuales del astronauta y su informe del día.

- **No se guarda en la base de datos**: solo queda en memoria del backend.
- Una llamada a Nova por astronauta por cada alerta nueva o cambio de nivel. Abrir otra
  repetición de la misma alerta devuelve la misma recomendación (`"cached": true`).
- Texto en inglés. `analysis.status`: `GENERATED`, `GENERATED_REVIEW_REQUIRED` o
  `GENERATED_PROTOCOL_REQUIRED` (hay una alerta urgente: el protocolo sigue mandando).
- Puede tardar hasta ~45 s: usa un tiempo de espera de al menos 60 s.

```json
{"notificationId": 12, "astronautId": 3, "generatedAt": "2026-11-15T12:00:05Z", "cached": false,
 "analysis": {"provider": "AWS_BEDROCK", "modelId": "us.amazon.nova-pro-v1:0", "language": "en",
   "status": "GENERATED_REVIEW_REQUIRED",
   "narrative": {"summary": "…", "uncertainties": ["…"]}, "activities": [],
   "disclaimer": "Experimental ground-station explanation, not a diagnosis…", "clinicallyValidated": false}}
```

| Código | Cuándo |
|---|---|
| 503 | IA desactivada (`NOTIFICATIONS_AI_ENABLED=false` o `BEDROCK_ANALYSIS_ENABLED=false`) o Nova no respondió / respuesta rechazada |
| 429 | Nova está procesando otra solicitud (máx. 1 a la vez, 10 s entre llamadas): reintenta |
| 400 | notificación manual |
| 404 | no existe |

La alerta y su guía nunca dependen de Nova: si la IA falla, la notificación sigue igual.

## Otros

| Método | Ruta | |
|---|---|---|
| GET | `/api/astronauts` (`?name=`) | listado |
| GET / DELETE | `/api/astronauts/{id}` | uno / borrar |
| GET | `/api/notifications` (`?astronautId=`) | alertas (`severity`: `WARNING` / `CRITICAL`) |
| GET | `/api/reports`, `/api/reports/{id}` | informes diarios |
| GET / PUT | `/api/settings` | configuración de la app |
| GET | `/healthz` | prueba de vida |

**Seguridad:** estas rutas no piden autenticación. Cualquiera que llegue al puerto 8080 puede
crear o borrar astronautas; limita el acceso con el Security Group y `CORS_ALLOWED_ORIGINS`.
