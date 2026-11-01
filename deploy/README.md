# Deploy del backend (AWS EC2 + RDS)

Copia de la configuración que corre en el servidor de producción. Los secretos reales viven solo en el servidor (`/etc/devutec.env`, con `chmod 600`) y nunca se suben al repositorio.

**API en producción:** https://devutec.duckdns.org

## Arquitectura

```
Cliente (web en Vercel / app Expo)
        │  HTTPS (443)
        ▼
Caddy  ── certificado de Let's Encrypt, renovación automática
        │  HTTP interno (localhost:8080)
        ▼
Spring Boot (servicio systemd `devutec`)
        │  red privada de la VPC
        ▼
PostgreSQL 16 en Amazon RDS (sin acceso público)
```

| Componente | Detalle |
|---|---|
| Servidor | EC2 `t3.small`, Amazon Linux 2023, Java 21 (Corretto) |
| IP fija | Elastic IP `52.7.186.192` |
| Dominio | `devutec.duckdns.org` (DuckDNS) apunta a la Elastic IP |
| HTTPS | Caddy como reverse proxy; obtiene y renueva el certificado solo |
| Base de datos | RDS PostgreSQL 16, `db.t4g.micro`, solo accesible desde la EC2 |

## Security groups

| Puerto | Origen | Uso |
|---|---|---|
| 443 (HTTPS) | 0.0.0.0/0 | API pública |
| 80 (HTTP) | 0.0.0.0/0 | Validación de Let's Encrypt y redirección a HTTPS |
| 22 (SSH) | Prefix list de EC2 Instance Connect | Administración desde la consola de AWS |
| 8080 | Cerrado al público | Solo Caddy accede, por dentro del servidor |

La base de datos está en un security group aparte que solo acepta conexiones desde la EC2.

## Despliegue continuo

Cada merge a `main` se despliega solo:

1. `devutec-autodeploy.timer` corre cada 5 minutos (y 2 minutos después de encender el servidor).
2. `devutec-autodeploy` compara el commit desplegado con `origin/main`. Si no hay cambios, termina.
3. Si los cambios no tocan el backend (`src/`, `pom.xml`, `mvnw`, `.mvn/`), por ejemplo cambios en `web/`, `mobile/`, `deploy/` o la documentación, solo actualiza el código del servidor y no reinicia la API.
4. Si cambió el backend, ejecuta `devutec-redeploy`: baja el código, compila, reinicia la app y verifica que la API responda.
5. Si la compilación falla, el script se detiene antes de reiniciar y la app sigue con la versión anterior.

No se usa GitHub Actions para desplegar porque requeriría abrir el puerto 22 a internet y guardar credenciales en GitHub; con este enfoque el servidor solo consulta GitHub y no se abre ningún puerto adicional. El CI (`.github/workflows/ci.yml`) sigue validando cada pull request antes del merge.

## Archivos

| Archivo | Ubicación en el servidor |
|---|---|
| `Caddyfile` | `/etc/caddy/Caddyfile` |
| `caddy.service` | `/etc/systemd/system/caddy.service` |
| `devutec.service` | `/etc/systemd/system/devutec.service` |
| `devutec.env.example` | Plantilla de `/etc/devutec.env` |
| `devutec-redeploy` | `/usr/local/bin/devutec-redeploy` |
| `devutec-autodeploy` | `/usr/local/bin/devutec-autodeploy` |
| `devutec-autodeploy.service` | `/etc/systemd/system/devutec-autodeploy.service` |
| `devutec-autodeploy.timer` | `/etc/systemd/system/devutec-autodeploy.timer` |

## Operación

```bash
# Desplegar a mano, sin esperar al temporizador
devutec-redeploy

# Ver qué hizo el despliegue automático
sudo journalctl -u devutec-autodeploy -n 30 --no-pager

# Ver los logs de la app
sudo journalctl -u devutec -n 50 --no-pager

# Estado de los servicios
systemctl status devutec caddy devutec-autodeploy.timer --no-pager
```

Al cambiar una variable de `/etc/devutec.env` (por ejemplo `CORS_ALLOWED_ORIGINS`), reiniciar la app con `sudo systemctl restart devutec`.

## Nota sobre AWS Academy Learner Lab

La instancia se detiene al terminar cada sesión del laboratorio. Al iniciar el lab, la Elastic IP mantiene la dirección y la app, Caddy y el despliegue automático arrancan solos, sin intervención manual.
