# orders-service (puerto 8083)

Pedidos de Pedidos360. Guarda el pedido en H2 y publica `order.created` en RabbitMQ (exchange `orders.direct`).

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
| `SPRING_PROFILES_ACTIVE` | — | `prod` en EC2 |

## Cómo probarlo
```bash
mvn clean package      # tests sin red ni broker (tokens firmados con llave RSA de prueba)
docker build -t orders-service .
curl -H "Authorization: Bearer <jwt>" localhost:8083/orders
```
