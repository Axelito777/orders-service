# orders-service (puerto 8083)

Pedidos de Pedidos360. Guarda el pedido en H2 y luego:
- **RabbitMQ:** publica `order.created` en `orders.direct` (stock) y `orders.topic` (notificaciones). Sin cambios.
- **Kafka:** publica el pedido en el tópico `order-events` (lo lee reportes-service). Key = `orderId`.
  Productor con `acks=all`, idempotente y `max.block.ms` corto: **si Kafka falla, solo se loguea y la compra
  responde igual**.

Mensaje en `order-events` (`fecha` es `LocalDateTime` sin zona, el mismo reloj que usaba reportes-service):
```json
{ "orderId": 42, "userId": "<oid>", "total": 199980.0, "fecha": "2026-11-26T15:30:00",
  "estado": "CREATED", "items": [ { "productId": 1, "productName": "Casio G-Shock", "quantity": 2, "unitPrice": 99990.0 } ] }
```

## Bloqueo antifraude
Antes de crear el pedido consulta a ms-pago `GET /payments/fraud-status/{oid}` reenviando el JWT del usuario.
Si está bloqueado responde **423** y no guarda ni publica nada:
```json
{ "error": "USER_BLOCKED", "until": "2026-11-26T16:40:00Z", "message": "Tu cuenta está bloqueada temporalmente por actividad sospechosa" }
```
**Fail-open:** si ms-pago no responde, da error o rechaza el token, el pedido se crea igual y se loguea una
advertencia (para llegar aquí el usuario ya pagó en ms-pago; rechazar dejaría cobros sin pedido).

## Endpoints
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/orders` | Crea un pedido (el `oid` y el correo salen del token) |
| GET | `/orders` | Pedidos del usuario del token |
| GET | `/orders/{id}` | Detalle |

Body de POST:
```json
{ "items": [ { "productId": 1, "productName": "Casio G-Shock", "quantity": 2, "unitPrice": 99990 } ] }
```

## Seguridad (JWT de Azure Entra ID)
El servicio valida el token por su cuenta, además del API Gateway (`config/SecurityConfig`):
- **Firma** con las llaves públicas de Azure (JWKS; se descargan con el primer token, no al arrancar).
- **Emisor** `https://login.microsoftonline.com/<tenant>/v2.0`, **audiencia** `api://<client-id>` o `<client-id>`
  y **expiración** (60 s de tolerancia).
- `scp` → `SCOPE_xxx`, `roles` → `ROLE_xxx`. Cualquier ruta no listada está denegada.
- **401**: sin token o token inválido (firma, emisor, audiencia, expirado). **403**: token válido sin el scope.

| Método | Ruta | Scope |
|---|---|---|
| POST | `/orders` | `orders.write` |
| GET | `/orders` | `orders.read` |
| GET | `/orders/{id}` | `orders.read` |

## Variables de entorno
| Variable | Por defecto | Descripción |
|---|---|---|
| `AZURE_TENANT_ID` | tenant del proyecto | Tenant de Entra ID (emisor y JWKS) |
| `AZURE_CLIENT_ID` | client ID de la API | Audiencia válida del token |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers de Kafka (EC2 de Kafka) |
| `KAFKA_MAX_BLOCK_MS` | `1500` | Máximo que se espera a Kafka al publicar |
| `MS_PAGO_URL` | `http://localhost:8087` | ms-pago (antifraude). **En la EC2: `http://ms-pago:8087`** (mismo docker-compose) |
| `MS_PAGO_TIMEOUT_MS` | `1500` | Espera máxima a ms-pago; si se supera, fail-open |
| `SPRING_PROFILES_ACTIVE` | — | `prod` en EC2 |

Copia `.env.example` a `.env` y complétalo.

## Cómo probarlo
```bash
mvn clean package      # tests sin red ni broker (tokens firmados con llave RSA de prueba)
docker build -t orders-service .
curl -H "Authorization: Bearer <jwt>" localhost:8083/orders
```
