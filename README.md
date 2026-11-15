# Backend-Deploy-con-Merken

Ground station: optional Amazon Bedrock Nova Pro explanations of physiological metrics in English. Android inference remains offline with its independent SLM.

See [configuration, API, privacy and limits](docs/bedrock-analysis.md).

`POST /api/analysis/preview` tests synthetic fixtures; `POST /api/analysis/astronauts/{id}` reads an existing record with explicit remote analysis consent. Both require a server-only `X-Analysis-Token`; disabled by default. No model-generated diagnosis, prescription or exercise clearance is authorized.

Frontends (JavaFX, web, móvil): [API de astronautas, rangos de salud, Garmin y CORS](docs/frontend-api.md).
