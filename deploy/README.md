# CI/CD: GitHub Actions → EC2

```
push a main ──► GitHub Actions ──► mvn verify (pruebas) ──► jar probado
                                                             │ scp
                                                             ▼
                                   EC2: deploy.sh ──► docker compose build + up
                                                  └─► espera /api/settings; si falla vuelve a la versión anterior
```

- **Pull request a `main`**: solo compila y ejecuta las pruebas (no despliega).
- **Push / merge a `main`**: pruebas y, si pasan, despliegue automático.
- **Manual**: pestaña *Actions* → *CI/CD backend* → *Run workflow*.

El jar se compila y prueba en GitHub; el EC2 solo construye una imagen liviana con ese jar (no necesita Maven).
Las credenciales **nunca** pasan por GitHub: el `.env` vive solo en el EC2.

## 1. Preparar el EC2 (una vez)

Desde la carpeta `backend` en tu PC (PowerShell o Git Bash):

```bash
scp -i backendRegistro.pem deploy/setup-ec2.sh ec2-user@34.233.92.102:~
ssh -i backendRegistro.pem ec2-user@34.233.92.102 "bash setup-ec2.sh"
```

Instala Docker, el plugin `docker compose` y `buildx`. Si Docker ya estaba instalado no pasa nada.

Crea el `.env` en el EC2 (copia de `.env.example` con tus valores):

```bash
ssh -i backendRegistro.pem ec2-user@34.233.92.102
nano ~/backend/.env         # DB_*, REPORTS_STORAGE, AWS_REGION, DYNAMODB_TABLE ...
chmod 600 ~/backend/.env
```

Si antes levantaste notificaciones o informes como contenedores aparte, detenlos:

```bash
docker rm -f astronaut-notifications astronaut-reports 2>/dev/null
```

## 2. Acceso a DynamoDB (informes)

**Opción recomendada: rol IAM en el EC2** (sin claves en archivos)

1. IAM → *Roles* → *Create role* → *AWS service: EC2*.
2. Agrega una política con permisos sobre la tabla:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "dynamodb:DescribeTable", "dynamodb:CreateTable", "dynamodb:UpdateTimeToLive",
      "dynamodb:DescribeTimeToLive", "dynamodb:PutItem", "dynamodb:BatchWriteItem",
      "dynamodb:UpdateItem", "dynamodb:GetItem", "dynamodb:Query", "dynamodb:Scan",
      "dynamodb:DeleteItem"
    ],
    "Resource": "arn:aws:dynamodb:us-east-1:*:table/astronaut-reports"
  }]
}
```

3. EC2 → la instancia → *Actions* → *Security* → *Modify IAM role* → elige el rol.
4. **Importante con Docker**: el contenedor necesita 2 "saltos" para leer las credenciales del rol.
   EC2 → instancia → *Actions* → *Instance settings* → *Modify instance metadata options* →
   **Metadata response hop limit = 2**.
   (o con AWS CLI: `aws ec2 modify-instance-metadata-options --instance-id i-xxxx --http-put-response-hop-limit 2 --http-endpoint enabled`)
5. En `~/backend/.env` deja vacías `AWS_ACCESS_KEY_ID` y `AWS_SECRET_ACCESS_KEY`.

**Alternativa**: usuario IAM con esa misma política y sus claves en `~/backend/.env`.

Sin acceso a AWS el backend igual funciona: guarda los informes en `~/backend/data/reports`
y la app muestra *"No se ha podido conectar a la base de datos... trabajando de forma local"*.
Cuando vuelve la conexión, sube lo local a DynamoDB.

## 3. Secrets en GitHub (una vez)

Repositorio → *Settings* → *Secrets and variables* → *Actions* → *New repository secret*:

| Secret | Valor |
|---|---|
| `EC2_HOST` | `34.233.92.102` (o la IP elástica / DNS público) |
| `EC2_USER` | `ec2-user` |
| `EC2_SSH_KEY` | todo el contenido de `backendRegistro.pem`, incluidas las líneas `-----BEGIN ...` y `-----END ...` |

Para copiar el .pem en Windows: `Get-Content backendRegistro.pem | Set-Clipboard`

Opcional: *Settings* → *Environments* → `production` → *Required reviewers* para aprobar cada despliegue.

## 4. Security Group del EC2

| Puerto | Origen | Para |
|---|---|---|
| 22 | `0.0.0.0/0` (los runners de GitHub no tienen IP fija) | SSH del pipeline |
| 8080 | tu IP o `0.0.0.0/0` | API que consume el frontend |

La seguridad del 22 la da la llave `.pem`; no la compartas y rota la llave si se filtra.
Usa una **IP elástica** para que `EC2_HOST` no cambie al reiniciar la instancia.

## 5. Desplegar

```bash
git add .
git commit -m "Monolito + CI/CD"
git push origin main
```

Sigue el progreso en la pestaña **Actions** del repositorio. Al terminar:

```bash
curl http://34.233.92.102:8080/api/astronauts
curl http://34.233.92.102:8080/api/reports/storage    # DYNAMODB o LOCAL
```

## Operación en el EC2

```bash
cd ~/backend
docker compose ps                    # estado
docker compose logs -f backend       # logs
cat .deployed-version                # commit desplegado
./deploy.sh manual                   # redesplegar a mano con el jar que ya está ahí
docker tag astronaut-backend:previous astronaut-backend:latest && docker compose up -d --force-recreate   # volver atrás a mano
```
